#include "UriFileHandler.h"

UriFileHandler::UriFileHandler(JniEnvHandler* jniEnvHandler, jobject uriFileHandler)
{
    this->jniEnvHandler = jniEnvHandler;
    this->uriFileHandler = uriFileHandler;
}

FILE* UriFileHandler::open(const char* path, const char* mode)
{
    JNIEnv* env = this->jniEnvHandler->getCurrentThreadEnv();

    jstring pathString = env->NewStringUTF(path);
    jstring modeString = env->NewStringUTF(mode);
    jclass handlerClass = env->GetObjectClass(this->uriFileHandler);
    jmethodID openMethod = env->GetMethodID(handlerClass, "open", "(Ljava/lang/String;Ljava/lang/String;)I");
    jint fileDescriptor = env->CallIntMethod(this->uriFileHandler, openMethod, pathString, modeString);

    if (fileDescriptor == -1) {
        return nullptr;
    } else {
        return fdopen(fileDescriptor, mode);
    }
}

UriFileHandler::~UriFileHandler()
{
}