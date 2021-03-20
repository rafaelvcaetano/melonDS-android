#ifndef MELONDS_ANDROID_JNIENVHANDLER_H
#define MELONDS_ANDROID_JNIENVHANDLER_H

#include <jni.h>

class JniEnvHandler {
private:
    JavaVM* vm;

public:
    JniEnvHandler(JavaVM* vm);
    JNIEnv* getCurrentThreadEnv();
};


#endif //MELONDS_ANDROID_JNIENVHANDLER_H
