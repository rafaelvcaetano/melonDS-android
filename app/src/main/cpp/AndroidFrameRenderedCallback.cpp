#include "AndroidFrameRenderedCallback.h"

AndroidFrameRenderedCallback::AndroidFrameRenderedCallback(JniEnvHandler* jniEnvHandler, jobject androidFrameRenderedListener)
{
    this->jniEnvHandler = jniEnvHandler;
    this->androidFrameRenderedListener = androidFrameRenderedListener;
}

void AndroidFrameRenderedCallback::onFrameRendered(int textureId)
{
    JNIEnv* env = this->jniEnvHandler->getCurrentThreadEnv();

    jclass listenerClass = env->GetObjectClass(this->androidFrameRenderedListener);
    jmethodID onFrameRenderedMethod = env->GetMethodID(listenerClass, "onFrameRendered", "(I)V");
    env->CallVoidMethod(this->androidFrameRenderedListener, onFrameRenderedMethod, textureId);
}