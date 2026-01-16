package me.magnum.melonds.domain.model.retroachievements

sealed class RAEvent {
    data class OnAchievementPrimed(val achievementId: Long) : RAEvent()
    data class OnAchievementUnPrimed(val achievementId: Long) : RAEvent()
    data class OnAchievementTriggered(val achievementId: Long) : RAEvent()
    data class OnAchievementProgressUpdated(val achievementId: Long, val current: Int, val target: Int, val progress: String) : RAEvent()
    data class OnLeaderboardAttemptStarted(val leaderboardId: Long) : RAEvent()
    data class OnLeaderboardAttemptUpdated(val leaderboardId: Long, val formattedValue: String) : RAEvent()
    data class OnLeaderboardAttemptCompleted(val leaderboardId: Long, val value: Int) : RAEvent()
    data class OnLeaderboardAttemptCancelled(val leaderboardId: Long) : RAEvent()
}