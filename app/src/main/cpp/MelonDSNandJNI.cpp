#include <jni.h>
#include <string>
#include <locale>
#include <codecvt>
#include "DSi_NAND.h"
#include "ROMManager.h"
#include "Platform.h"
#include "MelonDSAndroidConfiguration.h"
#include "MelonDS.h"
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

bool isNandOpen = false;

jobject getTitleData(JNIEnv* env, u32 category, u32 titleId);

extern "C"
{
JNIEXPORT jint JNICALL
Java_me_magnum_melonds_MelonDSiNand_openNand(JNIEnv* env, jobject thiz, jobject emulatorConfiguration)
{
    if (isNandOpen)
    {
        return NAND_INIT_ERROR_ALREADY_OPEN;
    }

    MelonDSAndroid::EmulatorConfiguration configuration = MelonDSAndroidConfiguration::buildEmulatorConfiguration(env, emulatorConfiguration);
    MelonDSAndroid::setConfiguration(configuration);

    auto bios7file = Platform::OpenLocalFile(configuration.dsiBios7Path, "rb");
    if (!bios7file)
    {
        return NAND_INIT_ERROR_BIOS7_NOT_FOUND;
    }

    u8 esKey[16];
    fseek(bios7file, 0x8308, SEEK_SET);
    fread(esKey, 16, 1, bios7file);
    fclose(bios7file);

    if (!DSi_NAND::Init(esKey))
    {
        return NAND_INIT_ERROR_NAND_FAILED;
    }

    isNandOpen = true;
    return NAND_INIT_OK;
}

JNIEXPORT jobject JNICALL
Java_me_magnum_melonds_MelonDSiNand_listTitles(JNIEnv* env, jobject thiz)
{
    const u32 category = DSI_NAND_FILE_CATEGORY;
    std::vector<u32> titleList;
    DSi_NAND::ListTitles(category, titleList);

    jclass listClass = env->FindClass("java/util/ArrayList");
    jmethodID listConstructor = env->GetMethodID(listClass, "<init>", "()V");
    jmethodID listAddMethod = env->GetMethodID(listClass, "add", "(ILjava/lang/Object;)V");
    jobject jniTitleList = env->NewObject(listClass, listConstructor);

    int index = 0;
    for (std::vector<u32>::iterator it = titleList.begin(); it != titleList.end(); it++)
    {
        u32 titleId = *it;
        jobject titleData = getTitleData(env, category, titleId);
        env->CallVoidMethod(jniTitleList, listAddMethod, index++, titleData);
    }

    return jniTitleList;
}

JNIEXPORT jint JNICALL
Java_me_magnum_melonds_MelonDSiNand_importTitle(JNIEnv* env, jobject thiz, jstring titleUri, jbyteArray tmdMetadata)
{
    if (!isNandOpen)
    {
        return TITLE_IMPORT_NAND_NOT_OPEN;
    }

    u32 titleId[2];

    const char* titlePath = env->GetStringUTFChars(titleUri, NULL);

    FILE* titleFile = Platform::OpenFile(titlePath, "rb");
    if (!titleFile)
    {
        env->ReleaseStringUTFChars(titleUri, titlePath);
        return TITLE_IMPORT_ERROR_OPENING_FILE;
    }

    fseek(titleFile, 0x230, SEEK_SET);
    fread(titleId, 8, 1, titleFile);
    fclose(titleFile);

    if (titleId[1] != DSI_NAND_FILE_CATEGORY)
    {
        // Not a DSiWare title
        env->ReleaseStringUTFChars(titleUri, titlePath);
        return TITLE_IMPORT_NOT_DSIWARE_TITLE;
    }

    if (DSi_NAND::TitleExists(titleId[1], titleId[0]))
    {
        // Title already exists
        env->ReleaseStringUTFChars(titleUri, titlePath);
        return TITLE_IMPORT_TITLE_ALREADY_IMPORTED;
    }

    jbyte* tmdBytes = env->GetByteArrayElements(tmdMetadata, NULL);

    DSi_NAND::DeleteTitle(titleId[0], titleId[1]);
    bool result = DSi_NAND::ImportTitle(titlePath, (u8*) tmdBytes, false);
    env->ReleaseStringUTFChars(titleUri, titlePath);
    env->ReleaseByteArrayElements(tmdMetadata, tmdBytes, 0);

    if (!result) {
        DSi_NAND::DeleteTitle(titleId[0], titleId[1]);
        return TITLE_IMPORT_INSATLL_FAILED;
    }

    return TITLE_IMPORT_OK;
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonDSiNand_deleteTitle(JNIEnv* env, jobject thiz, jint titleId)
{
    DSi_NAND::DeleteTitle(DSI_NAND_FILE_CATEGORY, (u32) titleId);
}

JNIEXPORT jboolean JNICALL
Java_me_magnum_melonds_MelonDSiNand_importTitleFile(JNIEnv* env, jobject thiz, jint titleId, jint fileType, jstring fileUri)
{
    jboolean isFilePathCopy;
    const char* filePath = env->GetStringUTFChars(fileUri, &isFilePathCopy);

    bool result = DSi_NAND::ImportTitleData(DSI_NAND_FILE_CATEGORY, (u32) titleId, fileType, filePath);

    if (isFilePathCopy)
    {
        env->ReleaseStringUTFChars(fileUri, filePath);
    }

    return result;
}

JNIEXPORT jboolean JNICALL
Java_me_magnum_melonds_MelonDSiNand_exportTitleFile(JNIEnv* env, jobject thiz, jint titleId, jint fileType, jstring fileUri)
{
    jboolean isFilePathCopy;
    const char* filePath = env->GetStringUTFChars(fileUri, &isFilePathCopy);

    bool result = DSi_NAND::ExportTitleData(DSI_NAND_FILE_CATEGORY, (u32) titleId, fileType, filePath);

    if (isFilePathCopy)
    {
        env->ReleaseStringUTFChars(fileUri, filePath);
    }

    return result;
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonDSiNand_closeNand(JNIEnv* env, jobject thiz)
{
    if (!isNandOpen)
    {
        return;
    }

    DSi_NAND::DeInit();
    isNandOpen = false;
}
}

jobject getTitleData(JNIEnv* env, u32 category, u32 titleId)
{
    u32 version;
    NDSHeader header;
    NDSBanner banner;

    DSi_NAND::GetTitleInfo(category, titleId, version, &header, &banner);

    u32 iconData[32 * 32];
    ROMManager::ROMIcon(banner.Icon, banner.Palette, iconData);
    jbyteArray iconBytes = env->NewByteArray(32 * 32 * sizeof(u32));
    jbyte* iconArrayElements = env->GetByteArrayElements(iconBytes, NULL);
    memcpy(iconArrayElements, iconData, sizeof(iconData));
    env->ReleaseByteArrayElements(iconBytes, iconArrayElements, 0);

    jclass dsiWareTitleClass = env->FindClass("me/magnum/melonds/domain/model/DSiWareTitle");
    jmethodID dsiWareTitleConstructor = env->GetMethodID(dsiWareTitleClass, "<init>", "(Ljava/lang/String;Ljava/lang/String;J[BJJI)V");

    std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t> convert;
    std::string englishTitle = convert.to_bytes(banner.EnglishTitle);

    size_t pos = englishTitle.find("\n");
    std::string title = englishTitle.substr(0, pos);
    std::string producer = englishTitle.substr(pos + 1);

    jobject titleObject = env->NewObject(
        dsiWareTitleClass,
        dsiWareTitleConstructor,
        env->NewStringUTF(title.c_str()),
        env->NewStringUTF(producer.c_str()),
        (jlong) titleId,
        iconBytes,
        (jlong) header.DSiPublicSavSize,
        (jlong) header.DSiPrivateSavSize,
        header.AppFlags
    );
    return titleObject;
}