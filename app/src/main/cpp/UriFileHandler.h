#ifndef MELONDS_ANDROID_URIFILEHANDLER_H
#define MELONDS_ANDROID_URIFILEHANDLER_H

#include <jni.h>
#include <stdio.h>
#include <AndroidFileHandler.h>
#include "JniEnvHandler.h"

class UriFileHandler : public MelonDSAndroid::AndroidFileHandler {
private:
    JniEnvHandler* jniEnvHandler;
    jobject uriFileHandler;

public:
    UriFileHandler(JniEnvHandler* jniEnvHandler, jobject uriFileHandler);
    FILE* open(const char* path, melonDS::Platform::FileMode mode);
    virtual ~UriFileHandler();

private:
    std::string getNativeAccessMode(melonDS::Platform::FileMode mode, bool fileExists);
    std::string getAccessMode(melonDS::Platform::FileMode mode, bool fileExists);
};

#endif //MELONDS_ANDROID_URIFILEHANDLER_H
