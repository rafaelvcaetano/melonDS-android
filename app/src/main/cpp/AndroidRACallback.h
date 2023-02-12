#ifndef ANDROIDRACALLBACK_H
#define ANDROIDRACALLBACK_H

#include "JniEnvHandler.h"
#include <retroachievements/RACallback.h>
#include <jni.h>

class AndroidRACallback : public RetroAchievements::RACallback
{
private:
    JniEnvHandler* jniEnvHandler;
    jobject callback;

public:
    AndroidRACallback(JniEnvHandler* jniEnvHandler, jobject callback);
    void onAchievementPrimed(long achievementId);
    void onAchievementTriggered(long achievementId);
    void onAchievementUnprimed(long achievementId);
};

#endif //ANDROIDRACALLBACK_H
