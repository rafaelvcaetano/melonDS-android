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

void AndroidRACallback::onAchievementProgressUpdated(long achievementId, unsigned int current, unsigned int target, std::string progress)
{
    JNIEnv* env = this->jniEnvHandler->getCurrentThreadEnv();

    jclass handlerClass = env->GetObjectClass(this->callback);
    jmethodID onAchievementProgressUpdatedMethod = env->GetMethodID(handlerClass, "onAchievementProgressUpdated", "(JIILjava/lang/String;)V");
    jstring progressString = env->NewStringUTF(progress.c_str());
    env->CallVoidMethod(this->callback, onAchievementProgressUpdatedMethod, achievementId, current, target, progressString);
}

void AndroidRACallback::onLeaderboardAttemptStarted(long leaderboardId)
{
    JNIEnv* env = this->jniEnvHandler->getCurrentThreadEnv();

    jclass handlerClass = env->GetObjectClass(this->callback);
    jmethodID onLeaderboardAttemptStartedMethod = env->GetMethodID(handlerClass, "onLeaderboardAttemptStarted", "(J)V");
    env->CallVoidMethod(this->callback, onLeaderboardAttemptStartedMethod, leaderboardId);
}

void AndroidRACallback::onLeaderboardAttemptUpdated(long leaderboardId, std::string formattedValue)
{
    JNIEnv* env = this->jniEnvHandler->getCurrentThreadEnv();

    jclass handlerClass = env->GetObjectClass(this->callback);
    jmethodID onLeaderboardAttemptUpdatedMethod = env->GetMethodID(handlerClass, "onLeaderboardAttemptUpdated", "(JLjava/lang/String;)V");
    jstring formattedValueString = env->NewStringUTF(formattedValue.c_str());
    env->CallVoidMethod(this->callback, onLeaderboardAttemptUpdatedMethod, leaderboardId, formattedValueString);
}

void AndroidRACallback::onLeaderboardAttemptCanceled(long leaderboardId)
{
    JNIEnv* env = this->jniEnvHandler->getCurrentThreadEnv();

    jclass handlerClass = env->GetObjectClass(this->callback);
    jmethodID onLeaderboardAttemptCompletedMethod = env->GetMethodID(handlerClass, "onLeaderboardAttemptCancelled", "(J)V");
    env->CallVoidMethod(this->callback, onLeaderboardAttemptCompletedMethod, leaderboardId);
}

void AndroidRACallback::onLeaderboardAttemptCompleted(long leaderboardId, int value)
{
    JNIEnv* env = this->jniEnvHandler->getCurrentThreadEnv();

    jclass handlerClass = env->GetObjectClass(this->callback);
    jmethodID onLeaderboardAttemptCancelledMethod = env->GetMethodID(handlerClass, "onLeaderboardAttemptCompleted", "(JI)V");
    env->CallVoidMethod(this->callback, onLeaderboardAttemptCancelledMethod, leaderboardId, value);
}

