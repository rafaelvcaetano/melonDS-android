#include "MelonDSAndroidRumbleManager.h"

MelonDSAndroidRumbleManager::MelonDSAndroidRumbleManager(JniEnvHandler* jniEnvHandler, jobject rumbleManager) : jniEnvHandler(jniEnvHandler), rumbleManager(rumbleManager)
{
}

void MelonDSAndroidRumbleManager::startRumble(u32 duration)
{
    if (rumbleManager == nullptr)
        return;

    JNIEnv* env = jniEnvHandler->getCurrentThreadEnv();
    jclass rumbleManagerClass = env->GetObjectClass(rumbleManager);
    jmethodID startRumbleMethod = env->GetMethodID(rumbleManagerClass, "startRumble", "(I)V");
    env->CallVoidMethod(rumbleManager, startRumbleMethod, (jint) duration);
}

void MelonDSAndroidRumbleManager::stopRumble()
{
    if (rumbleManager == nullptr)
        return;

    JNIEnv* env = jniEnvHandler->getCurrentThreadEnv();
    jclass rumbleManagerClass = env->GetObjectClass(rumbleManager);
    jmethodID stopRumbleMethod = env->GetMethodID(rumbleManagerClass, "stopRumble", "()V");
    env->CallVoidMethod(rumbleManager, stopRumbleMethod);
}

MelonDSAndroidRumbleManager::~MelonDSAndroidRumbleManager()
{
}
