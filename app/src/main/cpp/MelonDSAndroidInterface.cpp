#include "JniEnvHandler.h"
#include "UriFileHandler.h"
#include "MelonDS.h"
#include "OpenGLContext.h"

JniEnvHandler* jniEnvHandler;

JavaVM* vm;
jobject androidUriFileHandler;
UriFileHandler* fileHandler;

extern "C"
{
JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonDSAndroidInterface_setup(JNIEnv* env, jobject thiz, jobject uriFileHandler)
{
    env->GetJavaVM(&vm);
    jniEnvHandler = new JniEnvHandler(vm);
    androidUriFileHandler = env->NewGlobalRef(uriFileHandler);
    fileHandler = new UriFileHandler(jniEnvHandler, androidUriFileHandler);

    auto* openGlContext = new OpenGLContext();
    openGlContext->InitContext(0);

    MelonDSAndroid::openGlContext = openGlContext;
    MelonDSAndroid::fileHandler = fileHandler;
}

JNIEXPORT jlong JNICALL
Java_me_magnum_melonds_MelonDSAndroidInterface_getEmulatorGlContext(JNIEnv* env, jobject thiz)
{
    return (jlong) MelonDSAndroid::openGlContext->GetContext();
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonDSAndroidInterface_cleanup(JNIEnv* env, jobject thiz)
{
    env->DeleteGlobalRef(androidUriFileHandler);
    androidUriFileHandler = nullptr;
    vm = nullptr;

    MelonDSAndroid::openGlContext->DeInit();

    delete MelonDSAndroid::openGlContext;
    delete fileHandler;
    delete jniEnvHandler;

    MelonDSAndroid::openGlContext = nullptr;
}
}