#ifndef ANDROIDMELONEVENTMESSENGER_H
#define ANDROIDMELONEVENTMESSENGER_H

#include <MelonEventMessenger.h>

class AndroidMelonEventMessenger : public MelonDSAndroid::MelonEventMessenger
{
public:
    void onRumbleStart(int durationMs) override;
    void onRumbleStop() override;
    void onEmulatorStop(melonDS::Platform::StopReason reason) override;

    void onAchievementPrimed(long achievementId) override;
    void onAchievementTriggered(long achievementId) override;
    void onAchievementUnprimed(long achievementId) override;
    void onAchievementProgressUpdated(long achievementId, unsigned int current, unsigned int target, std::string progress) override;
    void onLeaderboardAttemptStarted(long leaderboardId) override;
    void onLeaderboardAttemptUpdated(long leaderboardId, std::string formattedValue) override;
    void onLeaderboardAttemptCanceled(long leaderboardId) override;
    void onLeaderboardAttemptCompleted(long leaderboardId, int value) override;

private:
    // Event type constants
    static constexpr int EVENT_RUMBLE_START = 100;
    static constexpr int EVENT_RUMBLE_STOP = 101;
    static constexpr int EVENT_EMULATOR_STOP = 102;

    static constexpr int EVENT_RA_ACHIEVEMENT_PRIMED = 200;
    static constexpr int EVENT_RA_ACHIEVEMENT_TRIGGERED = 201;
    static constexpr int EVENT_RA_ACHIEVEMENT_UNPRIMED = 202;
    static constexpr int EVENT_RA_ACHIEVEMENT_PROGRESS_UPDATED = 203;
    static constexpr int EVENT_RA_LBOARD_ATTEMPT_STARTED = 210;
    static constexpr int EVENT_RA_LBOARD_ATTEMPT_UPDATED = 211;
    static constexpr int EVENT_RA_LBOARD_ATTEMPT_CANCELED = 212;
    static constexpr int EVENT_RA_LBOARD_ATTEMPT_COMPLETED = 213;
};

#endif // ANDROIDMELONEVENTMESSENGER_H
