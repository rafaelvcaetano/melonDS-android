package me.magnum.melonds.common

interface RetroAchievementsCallback {
    fun onAchievementPrimed(achievementId: Long)
    fun onAchievementTriggered(achievementId: Long)
    fun onAchievementUnprimed(achievementId: Long)
    fun onAchievementProgressUpdated(achievementId: Long, current: Int, target: Int, progress: String)
    fun onLeaderboardAttemptStarted(leaderboardId: Long)
    fun onLeaderboardAttemptUpdated(leaderboardId: Long, formattedValue: String)
    fun onLeaderboardAttemptCompleted(leaderboardId: Long, value: Int)
    fun onLeaderboardAttemptCancelled(leaderboardId: Long)
}