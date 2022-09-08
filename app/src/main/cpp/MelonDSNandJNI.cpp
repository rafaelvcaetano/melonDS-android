#include <jni.h>
#include <string>
#include <locale>
#include <codecvt>
#include "DSi_NAND.h"
#include "FrontendUtil.h"
#include "Platform.h"
#include "MelonDSAndroidConfiguration.h"
#include "MelonDS.h"
#include "UriFileHandler.h"

#define NAND_INIT_OK 0
#define NAND_INIT_ERROR_ALREADY_OPEN 1
#define NAND_INIT_ERROR_BIOS7_FAILED 2
#define NAND_INIT_ERROR_NAND_FAILED 3

FILE* nandFile = nullptr;

jobject getTitleData(JNIEnv* env, u32 category, u32 titleId);

extern "C"
{
JNIEXPORT jint JNICALL
Java_me_magnum_melonds_MelonDSiNand_openNand(JNIEnv* env, jobject thiz, jobject emulatorConfiguration)
{
    if (nandFile != nullptr)
    {
        return NAND_INIT_ERROR_ALREADY_OPEN;
    }

    MelonDSAndroid::EmulatorConfiguration configuration = MelonDSAndroidConfiguration::buildEmulatorConfiguration(env, emulatorConfiguration);

    auto bios7file = Platform::OpenLocalFile(configuration.dsBios7Path, "rb");
    if (!bios7file)
    {
        return NAND_INIT_ERROR_BIOS7_FAILED;
    }

    u8 esKey[16];
    fseek(bios7file, 0x8303, SEEK_SET);
    fread(esKey, 16, 1, bios7file);
    fclose(bios7file);

    nandFile = Platform::OpenLocalFile(configuration.dsiNandPath, "r+b");
    if (!DSi_NAND::Init(nandFile, esKey))
    {
        fclose(nandFile);
        nandFile = nullptr;
        return NAND_INIT_ERROR_NAND_FAILED;
    }

    return NAND_INIT_OK;
}

JNIEXPORT jobject JNICALL
Java_me_magnum_melonds_MelonDSiNand_listTitles(JNIEnv* env, jobject thiz)
{
    const u32 category = 0x00030004;
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

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonDSiNand_importTitle(JNIEnv* env, jobject thiz, jstring titleUri, jbyteArray tmdMetadata)
{
    if (nandFile == nullptr)
    {
        return;
    }

    u32 titleId[2];

    const char* titlePath = env->GetStringUTFChars(titleUri, NULL);

    FILE* titleFile = Platform::OpenFile(titlePath, "rb");
    if (!titleFile)
    {
        env->ReleaseStringUTFChars(titleUri, titlePath);
        return;
    }

    fseek(titleFile, 0x230, SEEK_SET);
    fread(titleId, 8, 1, titleFile);
    fclose(titleFile);

    if (titleId[1] != 0x00030004)
    {
        // Not a DSiWare title
        env->ReleaseStringUTFChars(titleUri, titlePath);
        return;
    }

    if (DSi_NAND::TitleExists(titleId[1], titleId[0]))
    {
        // Title already exists
        env->ReleaseStringUTFChars(titleUri, titlePath);
        return;
    }

    jbyte* tmdBytes = env->GetByteArrayElements(tmdMetadata, NULL);

    DSi_NAND::DeleteTitle(titleId[0], titleId[1]);
    bool result = DSi_NAND::ImportTitle(titlePath, (u8*) tmdBytes, false);
    if (!result) {
        DSi_NAND::DeleteTitle(titleId[0], titleId[1]);
    }

    env->ReleaseStringUTFChars(titleUri, titlePath);
    env->ReleaseByteArrayElements(tmdMetadata, tmdBytes, 0);
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonDSiNand_deleteTitle(JNIEnv* env, jobject thiz, jint titleId)
{
    DSi_NAND::DeleteTitle(0x00030004, (u32) titleId);
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonDSiNand_closeNand(JNIEnv* env, jobject thiz)
{
    if (nandFile == nullptr)
    {
        return;
    }

    DSi_NAND::DeInit();
    fclose(nandFile);
    nandFile = nullptr;
}
}

jobject getTitleData(JNIEnv* env, u32 category, u32 titleId)
{
    u32 version;
    NDSHeader header;
    NDSBanner banner;

    DSi_NAND::GetTitleInfo(category, titleId, version, &header, &banner);

    u32 iconData[32 * 32];
    Frontend::ROMIcon(banner.Icon, banner.Palette, iconData);
    jbyteArray iconBytes = env->NewByteArray(32 * 32 * sizeof(u32));
    jbyte* iconArrayElements = env->GetByteArrayElements(iconBytes, NULL);
    memcpy(iconArrayElements, iconData, sizeof(iconData));
    env->ReleaseByteArrayElements(iconBytes, iconArrayElements, 0);

    jclass dsiWareTitleClass = env->FindClass("me/magnum/melonds/domain/model/DSiWareTitle");
    jmethodID dsiWareTitleConstructor = env->GetMethodID(dsiWareTitleClass, "<init>", "(Ljava/lang/String;Ljava/lang/String;J[B)V");

    std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t> convert;
    std::string englishTitle = convert.to_bytes(banner.EnglishTitle);

    size_t pos = 0;
    pos = englishTitle.find("\n");
    std::string title = englishTitle.substr(0, pos);
    std::string producer = englishTitle.substr(pos + 1);

    jobject titleObject = env->NewObject(dsiWareTitleClass, dsiWareTitleConstructor, env->NewStringUTF(title.c_str()), env->NewStringUTF(producer.c_str()), (jlong) titleId, iconBytes);
    return titleObject;
}