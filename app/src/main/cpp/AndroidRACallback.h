#ifndef ANDROIDRACALLBACK_H
#define ANDROIDRACALLBACK_H

#include "JniEnvHandler.h"
#include <retroachievements/RACallback.h>
#include <jni.h>

class AndroidRACallback : public MelonDSAndroid::RetroAchievements::RACallback
{
private:
    JniEnvHandler* jniEnvHandler;
    jobject callback;

public:
    AndroidRACallback(JniEnvHandler* jniEnvHandler, jobject callback);
    void onAchievementPrimed(long achievementId);
    void onAchievementTriggered(long achievementId);
    void onAchievementUnprimed(long achievementId);
    void onAchievementProgressUpdated(long achievementId, unsigned int current, unsigned int target, std::string progress);
    void onLeaderboardAttemptStarted(long leaderboardId);
    void onLeaderboardAttemptUpdated(long leaderboardId, std::string formattedValue);
    void onLeaderboardAttemptCanceled(long leaderboardId);
    void onLeaderboardAttemptCompleted(long leaderboardId, int value);
};

#endif //ANDROIDRACALLBACK_H
