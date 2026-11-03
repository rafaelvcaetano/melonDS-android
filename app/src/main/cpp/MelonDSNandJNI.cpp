#include <jni.h>
#include <string>
#include <algorithm>
#include "DSi_NAND.h"
#include "ROMManager.h"
#include "Platform.h"
#include "MelonDSAndroidConfiguration.h"
#include "MelonDS.h"
#include "RomIconBuilder.h"
#include "UriFileHandler.h"

#define NAND_INIT_OK 0
#define NAND_INIT_ERROR_ALREADY_OPEN 1
#define NAND_INIT_ERROR_BIOS7_NOT_FOUND 2
#define NAND_INIT_ERROR_NAND_FAILED 3

#define TITLE_IMPORT_OK 0
#define TITLE_IMPORT_NAND_NOT_OPEN 1
#define TITLE_IMPORT_ERROR_OPENING_FILE 2
#define TITLE_IMPORT_NOT_DSIWARE_TITLE 3
#define TITLE_IMPORT_TITLE_ALREADY_IMPORTED 4
#define TITLE_IMPORT_INSATLL_FAILED 5

const u32 DSI_NAND_FILE_CATEGORY = 0x00030004;

std::unique_ptr<melonDS::DSi_NAND::NANDImage> nand;
melonDS::DSi_NAND::NANDMount* nandMount;

jobject getTitleData(JNIEnv* env, u32 category, u32 titleId);

extern "C"
{
JNIEXPORT jint JNICALL
Java_me_magnum_melonds_MelonDSiNand_openNand(JNIEnv* env, jobject thiz, jobject emulatorConfiguration)
{
    if (nand)
        return NAND_INIT_ERROR_ALREADY_OPEN;

    MelonDSAndroid::EmulatorConfiguration configuration = MelonDSAndroidConfiguration::buildEmulatorConfiguration(env, emulatorConfiguration);
    const std::string bios7Path = configuration.dsiBios7Path;
    const std::string nandPath = configuration.dsiNandPath;
    MelonDSAndroid::setConfiguration(std::move(configuration));

    auto bios7file = Platform::OpenFile(bios7Path, melonDS::Platform::FileMode::Read);
    if (!bios7file)
        return NAND_INIT_ERROR_BIOS7_NOT_FOUND;

    u8 esKey[16];
    const bool hasEsKey = Platform::FileSeek(bios7file, 0x8308, melonDS::Platform::FileSeekOrigin::Start)
        && Platform::FileRead(esKey, sizeof(esKey), 1, bios7file) == 1;
    Platform::CloseFile(bios7file);
    if (!hasEsKey)
        return NAND_INIT_ERROR_BIOS7_NOT_FOUND;

    auto nandfile = Platform::OpenLocalFile(nandPath, melonDS::Platform::FileMode::ReadWriteExisting);
    if (!nandfile)
        return NAND_INIT_ERROR_NAND_FAILED;

    nand = std::make_unique<melonDS::DSi_NAND::NANDImage>(nandfile, esKey);
    if (!*nand)
    {
        nand = nullptr;
        return NAND_INIT_ERROR_NAND_FAILED;
    }

    nandMount = new melonDS::DSi_NAND::NANDMount(*nand);

    return NAND_INIT_OK;
}

JNIEXPORT jobject JNICALL
Java_me_magnum_melonds_MelonDSiNand_listTitles(JNIEnv* env, jobject thiz)
{
    if (!nandMount)
        return nullptr;
    const u32 category = DSI_NAND_FILE_CATEGORY;
    std::vector<u32> titleList;
    nandMount->ListTitles(category, titleList);

    jclass listClass = env->FindClass("java/util/ArrayList");
    if (!listClass || env->ExceptionCheck())
        return nullptr;
    jmethodID listConstructor = env->GetMethodID(listClass, "<init>", "()V");
    jmethodID listAddMethod = env->GetMethodID(listClass, "add", "(Ljava/lang/Object;)Z");
    if (!listConstructor || !listAddMethod || env->ExceptionCheck())
    {
        env->DeleteLocalRef(listClass);
        return nullptr;
    }
    jobject jniTitleList = env->NewObject(listClass, listConstructor);
    if (!jniTitleList || env->ExceptionCheck())
    {
        env->DeleteLocalRef(listClass);
        return nullptr;
    }
    env->DeleteLocalRef(listClass);

    for (std::vector<u32>::iterator it = titleList.begin(); it != titleList.end(); it++)
    {
        if (env->PushLocalFrame(8) < 0)
            return nullptr;
        u32 titleId = *it;
        jobject titleData = getTitleData(env, category, titleId);
        if (!titleData)
        {
            env->PopLocalFrame(nullptr);
            if (env->ExceptionCheck()) return nullptr;
            continue;
        }
        env->CallBooleanMethod(jniTitleList, listAddMethod, titleData);
        env->PopLocalFrame(nullptr);
        if (env->ExceptionCheck()) return nullptr;
    }

    return jniTitleList;
}

JNIEXPORT jint JNICALL
Java_me_magnum_melonds_MelonDSiNand_importTitle(JNIEnv* env, jobject thiz, jstring titleUri, jbyteArray tmdMetadata)
{
    if (!nand || !nandMount)
        return TITLE_IMPORT_NAND_NOT_OPEN;

    melonDS::DSi_TMD::TitleMetadata titleMetadata;
    if (!tmdMetadata || env->GetArrayLength(tmdMetadata) < static_cast<jint>(sizeof(titleMetadata)))
        return TITLE_IMPORT_INSATLL_FAILED;
    env->GetByteArrayRegion(tmdMetadata, 0, sizeof(titleMetadata), reinterpret_cast<jbyte*>(&titleMetadata));
    if (env->ExceptionCheck())
        return TITLE_IMPORT_INSATLL_FAILED;

    u32 titleId[2];

    const char* titlePath = env->GetStringUTFChars(titleUri, NULL);

    auto titleFile = Platform::OpenFile(titlePath, melonDS::Platform::FileMode::Read);
    if (!titleFile)
    {
        env->ReleaseStringUTFChars(titleUri, titlePath);
        return TITLE_IMPORT_ERROR_OPENING_FILE;
    }

    const bool hasTitleId = Platform::FileSeek(titleFile, 0x230, melonDS::Platform::FileSeekOrigin::Start)
        && Platform::FileRead(titleId, sizeof(titleId), 1, titleFile) == 1;
    Platform::CloseFile(titleFile);

    if (!hasTitleId || titleId[1] != DSI_NAND_FILE_CATEGORY)
    {
        // Not a DSiWare title
        env->ReleaseStringUTFChars(titleUri, titlePath);
        return TITLE_IMPORT_NOT_DSIWARE_TITLE;
    }

    // The core uses the TMD identity for destination paths. Reject a different
    // title before cleanup/import so malformed metadata cannot target another title.
    if (titleMetadata.GetCategory() != titleId[1] || titleMetadata.GetID() != titleId[0])
    {
        env->ReleaseStringUTFChars(titleUri, titlePath);
        return TITLE_IMPORT_INSATLL_FAILED;
    }

    if (nandMount->TitleExists(titleId[1], titleId[0]))
    {
        // Title already exists
        env->ReleaseStringUTFChars(titleUri, titlePath);
        return TITLE_IMPORT_TITLE_ALREADY_IMPORTED;
    }

    nandMount->DeleteTitle(titleId[1], titleId[0]);
    bool result = nandMount->ImportTitle(titlePath, titleMetadata, false);

    env->ReleaseStringUTFChars(titleUri, titlePath);

    if (!result)
    {
        nandMount->DeleteTitle(titleId[1], titleId[0]);
        return TITLE_IMPORT_INSATLL_FAILED;
    }

    return TITLE_IMPORT_OK;
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonDSiNand_deleteTitle(JNIEnv* env, jobject thiz, jint titleId)
{
    if (nand && nandMount)
        nandMount->DeleteTitle(DSI_NAND_FILE_CATEGORY, (u32) titleId);
}

JNIEXPORT jboolean JNICALL
Java_me_magnum_melonds_MelonDSiNand_importTitleFile(JNIEnv* env, jobject thiz, jint titleId, jint fileType, jstring fileUri)
{
    if (!nandMount)
        return false;
    const char* filePath = env->GetStringUTFChars(fileUri, nullptr);
    if (!filePath)
        return false;

    bool result = nandMount->ImportTitleData(DSI_NAND_FILE_CATEGORY, (u32) titleId, fileType, filePath);

    env->ReleaseStringUTFChars(fileUri, filePath);

    return result;
}

JNIEXPORT jboolean JNICALL
Java_me_magnum_melonds_MelonDSiNand_exportTitleFile(JNIEnv* env, jobject thiz, jint titleId, jint fileType, jstring fileUri)
{
    if (!nandMount)
        return false;
    const char* filePath = env->GetStringUTFChars(fileUri, nullptr);
    if (!filePath)
        return false;

    bool result = nandMount->ExportTitleData(DSI_NAND_FILE_CATEGORY, (u32) titleId, fileType, filePath);

    env->ReleaseStringUTFChars(fileUri, filePath);

    return result;
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonDSiNand_closeNand(JNIEnv* env, jobject thiz)
{
    if (!nand)
        return;

    delete nandMount;
    nandMount = nullptr;
    nand = nullptr;
}
}

jobject getTitleData(JNIEnv* env, u32 category, u32 titleId)
{
    if (!nandMount)
        return nullptr;
    u32 version;
    NDSHeader header;
    NDSBanner banner;

    if (!nandMount->GetTitleInfo(category, titleId, version, &header, &banner))
        return nullptr;

    u32 iconData[32 * 32];
    MelonDSAndroid::BuildRomIcon(banner.Icon, banner.Palette, iconData);
    jbyteArray iconBytes = env->NewByteArray(32 * 32 * sizeof(u32));
    if (!iconBytes || env->ExceptionCheck())
        return nullptr;
    jbyte* iconArrayElements = env->GetByteArrayElements(iconBytes, NULL);
    if (!iconArrayElements)
    {
        env->DeleteLocalRef(iconBytes);
        return nullptr;
    }
    memcpy(iconArrayElements, iconData, sizeof(iconData));
    env->ReleaseByteArrayElements(iconBytes, iconArrayElements, 0);

    jclass dsiWareTitleClass = env->FindClass("me/magnum/melonds/domain/model/DSiWareTitle");
    if (!dsiWareTitleClass || env->ExceptionCheck())
    {
        env->DeleteLocalRef(iconBytes);
        return nullptr;
    }
    jmethodID dsiWareTitleConstructor = env->GetMethodID(dsiWareTitleClass, "<init>", "(Ljava/lang/String;Ljava/lang/String;J[BJJI)V");
    if (!dsiWareTitleConstructor || env->ExceptionCheck())
    {
        env->DeleteLocalRef(iconBytes);
        env->DeleteLocalRef(dsiWareTitleClass);
        return nullptr;
    }

    const char16_t* titleBegin = std::begin(banner.EnglishTitle);
    const char16_t* titleLimit = titleBegin + std::size(banner.EnglishTitle);
    const char16_t* titleEnd = std::find(titleBegin, titleLimit, u'\0');
    const char16_t* separator = std::find(titleBegin, titleEnd, u'\n');
    const char16_t* producerBegin = separator == titleEnd ? titleEnd : separator + 1;

    // JNI NewString copies a bounded UTF-16 sequence directly. Unlike
    // wstring_convert::to_bytes, it cannot throw std::range_error for an
    // isolated surrogate supplied by a malformed banner.
    jstring jniTitle = env->NewString(reinterpret_cast<const jchar*>(titleBegin),
        static_cast<jsize>(separator - titleBegin));
    jstring jniProducer = env->NewString(reinterpret_cast<const jchar*>(producerBegin),
        static_cast<jsize>(titleEnd - producerBegin));
    if (!jniTitle || !jniProducer || env->ExceptionCheck())
    {
        env->DeleteLocalRef(jniTitle);
        env->DeleteLocalRef(jniProducer);
        env->DeleteLocalRef(iconBytes);
        env->DeleteLocalRef(dsiWareTitleClass);
        return nullptr;
    }
    jobject titleObject = env->NewObject(
        dsiWareTitleClass,
        dsiWareTitleConstructor,
        jniTitle,
        jniProducer,
        (jlong) titleId,
        iconBytes,
        (jlong) header.DSiPublicSavSize,
        (jlong) header.DSiPrivateSavSize,
        header.AppFlags
    );
    // The Java constructor stores these objects. Only return the title's local handle.
    env->DeleteLocalRef(jniTitle);
    env->DeleteLocalRef(jniProducer);
    env->DeleteLocalRef(iconBytes);
    env->DeleteLocalRef(dsiWareTitleClass);
    return titleObject;
}
