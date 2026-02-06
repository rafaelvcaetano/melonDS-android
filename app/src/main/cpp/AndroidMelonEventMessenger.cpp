#include "AndroidMelonEventMessenger.h"
#include "EmulatorMessageQueueJNI.h"

void AndroidMelonEventMessenger::onRumbleStart(int durationMs)
{
    MelonDSAndroid::fireEmulatorEvent(EVENT_RUMBLE_START, sizeof(durationMs), &durationMs);
}

void AndroidMelonEventMessenger::onRumbleStop()
{
    MelonDSAndroid::fireEmulatorEvent(EVENT_RUMBLE_STOP);
}

void AndroidMelonEventMessenger::onEmulatorStop(melonDS::Platform::StopReason reason)
{
    int32_t reasonInt = (int32_t) reason;
    MelonDSAndroid::fireEmulatorEvent(EVENT_EMULATOR_STOP, sizeof(reasonInt), &reasonInt);
}

void AndroidMelonEventMessenger::onAchievementPrimed(long achievementId)
{
    int64_t achievementIdLong = (int64_t) achievementId;
    MelonDSAndroid::fireEmulatorEvent(EVENT_RA_ACHIEVEMENT_PRIMED, sizeof(achievementIdLong), &achievementIdLong);
}

void AndroidMelonEventMessenger::onAchievementTriggered(long achievementId)
{
    int64_t achievementIdLong = (int64_t) achievementId;
    MelonDSAndroid::fireEmulatorEvent(EVENT_RA_ACHIEVEMENT_TRIGGERED, sizeof(achievementIdLong), &achievementIdLong);
}

void AndroidMelonEventMessenger::onAchievementUnprimed(long achievementId)
{
    int64_t achievementIdLong = (int64_t) achievementId;
    MelonDSAndroid::fireEmulatorEvent(EVENT_RA_ACHIEVEMENT_UNPRIMED, sizeof(achievementIdLong), &achievementIdLong);
}

void AndroidMelonEventMessenger::onAchievementProgressUpdated(long achievementId, unsigned int current, unsigned int target, std::string progress)
{
    struct {
        int64_t achievementId;
        int32_t current;
        int32_t target;
        int32_t progressSize;
        char progress[32];
    } data = {
        .achievementId = (int64_t) achievementId,
        .current = (int32_t) current,
        .target = (int32_t) target,
        .progressSize = (int32_t) progress.size(),
    };
    strncpy(data.progress, progress.c_str(), sizeof(data.progress));

    MelonDSAndroid::fireEmulatorEvent(EVENT_RA_ACHIEVEMENT_PROGRESS_UPDATED, sizeof(data), &data);
}

void AndroidMelonEventMessenger::onLeaderboardAttemptStarted(long leaderboardId)
{
    int64_t leaderboardIdLong = (int64_t) leaderboardId;
    MelonDSAndroid::fireEmulatorEvent(EVENT_RA_LBOARD_ATTEMPT_STARTED, sizeof(leaderboardIdLong), &leaderboardIdLong);
}

void AndroidMelonEventMessenger::onLeaderboardAttemptUpdated(long leaderboardId, std::string formattedValue)
{
    struct {
        int64_t leaderboardId;
        int32_t formattedValueSize;
        char formattedValue[32];
    } data = {
        .leaderboardId = (int64_t) leaderboardId,
        .formattedValueSize = (int32_t) formattedValue.size(),
    };
    strncpy(data.formattedValue, formattedValue.c_str(), sizeof(data.formattedValue));

    MelonDSAndroid::fireEmulatorEvent(EVENT_RA_LBOARD_ATTEMPT_UPDATED, sizeof(data), &data);
}

void AndroidMelonEventMessenger::onLeaderboardAttemptCanceled(long leaderboardId)
{
    int64_t leaderboardIdLong = (int64_t) leaderboardId;
    MelonDSAndroid::fireEmulatorEvent(EVENT_RA_LBOARD_ATTEMPT_CANCELED, sizeof(leaderboardIdLong), &leaderboardIdLong);
}

void AndroidMelonEventMessenger::onLeaderboardAttemptCompleted(long leaderboardId, int value)
{
    struct {
        int64_t leaderboardId;
        int32_t value;
    } data = {
        .leaderboardId = (int64_t) leaderboardId,
        .value = (int32_t) value,
    };

    MelonDSAndroid::fireEmulatorEvent(EVENT_RA_LBOARD_ATTEMPT_COMPLETED, sizeof(data), &data);
}