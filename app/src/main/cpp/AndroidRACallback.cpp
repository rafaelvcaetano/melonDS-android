#include "AndroidRACallback.h"

AndroidRACallback::AndroidRACallback(JniEnvHandler* jniEnvHandler, jobject callback)
{
    this->jniEnvHandler = jniEnvHandler;
    this->callback = callback;
}

void AndroidRACallback::onAchievementPrimed(long achievementId)
{
    JNIEnv* env = this->jniEnvHandler->getCurrentThreadEnv();

    jclass handlerClass = env->GetObjectClass(this->callback);
    jmethodID onAchievementPrimedMethod = env->GetMethodID(handlerClass, "onAchievementPrimed", "(J)V");
    env->CallVoidMethod(this->callback, onAchievementPrimedMethod, achievementId);
}

void AndroidRACallback::onAchievementTriggered(long achievementId)
{
    JNIEnv* env = this->jniEnvHandler->getCurrentThreadEnv();

    jclass handlerClass = env->GetObjectClass(this->callback);
    jmethodID onAchievementTriggeredMethod = env->GetMethodID(handlerClass, "onAchievementTriggered", "(J)V");
    env->CallVoidMethod(this->callback, onAchievementTriggeredMethod, achievementId);
}

void AndroidRACallback::onAchievementUnprimed(long achievementId)
{
    JNIEnv* env = this->jniEnvHandler->getCurrentThreadEnv();

    jclass handlerClass = env->GetObjectClass(this->callback);
    jmethodID onAchievementUnprimedMethod = env->GetMethodID(handlerClass, "onAchievementUnprimed", "(J)V");
    env->CallVoidMethod(this->callback, onAchievementUnprimedMethod, achievementId);
}