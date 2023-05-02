package me.magnum.melonds.domain.model.retroachievements

import java.net.URL

data class GameAchievementData(
    val isRetroAchievementsIntegrationEnabled: Boolean,
    val lockedAchievements: List<RASimpleAchievement>,
    val totalAchievementCount: Int,
    val richPresencePatch: String?,
    val icon: URL?,
) {

    val unlockedAchievementCount get() = totalAchievementCount - lockedAchievements.size

    companion object {
        fun withDisabledRetroAchievementsIntegration(): GameAchievementData {
            return GameAchievementData(false, emptyList(), 0, null, null)
        }
    }
}