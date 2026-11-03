#include "UriFileHandler.h"
#include "FileModeString.h"
#include "Platform.h"
#include <unistd.h>

using namespace melonDS::Platform;

UriFileHandler::UriFileHandler(JniEnvHandler* jniEnvHandler, jobject uriFileHandler)
{
    this->jniEnvHandler = jniEnvHandler;
    this->uriFileHandler = uriFileHandler;
}

FILE* UriFileHandler::open(const char* path, FileMode mode)
{
    JNIEnv* env = this->jniEnvHandler->getCurrentThreadEnv();
    if (!env || env->PushLocalFrame(8) < 0) return nullptr;

    // The Kotlin side creates the document when it is missing, so the mode strings are built
    // as if the file were not there yet. That matters for one case only: Read|Write|Preserve
    // asks SAF for "rw" (an existing document opened without truncation) while the native
    // string comes out as "w+b". fdopen() never truncates -- truncation is decided by open(2),
    // which already happened on the Kotlin side -- so the file keeps its contents either way.
    jstring pathString = env->NewStringUTF(path);
    jstring modeString = env->NewStringUTF(MelonDSAndroid::GetSafModeString(mode, false).c_str());
    jclass handlerClass = env->GetObjectClass(this->uriFileHandler);
    jmethodID openMethod = env->GetMethodID(handlerClass, "open", "(Ljava/lang/String;Ljava/lang/String;)I");
    jint fileDescriptor = -1;
    if (pathString && modeString && handlerClass && openMethod)
        fileDescriptor = env->CallIntMethod(this->uriFileHandler, openMethod, pathString, modeString);
    if (env->ExceptionCheck())
    {
        env->ExceptionClear();
        fileDescriptor = -1;
    }
    env->PopLocalFrame(nullptr);

    if (fileDescriptor == -1) {
        return nullptr;
    } else {
        std::string nativeMode = MelonDSAndroid::GetStdioModeString(mode, false);
        FILE* file = fdopen(fileDescriptor, nativeMode.c_str());
        if (!file) close(fileDescriptor);
        return file;
    }
}

UriFileHandler::~UriFileHandler()
{
}
