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
    FILE* open(const char* path, const char* mode);
    virtual ~UriFileHandler();
};

#endif //MELONDS_ANDROID_URIFILEHANDLER_H
