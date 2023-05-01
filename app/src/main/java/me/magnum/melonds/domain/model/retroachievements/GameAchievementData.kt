package me.magnum.melonds.domain.model.retroachievements

data class GameAchievementData(
    val isRetroAchievementsIntegrationEnabled: Boolean,
    val lockedAchievements: List<RASimpleAchievement>,
    val totalAchievementCount: Int,
    val richPresencePatch: String?,
) {

    val unlockedAchievementCount get() = totalAchievementCount - lockedAchievements.size

    companion object {
        fun withDisabledRetroAchievementsIntegration(): GameAchievementData {
            return GameAchievementData(false, emptyList(), 0, null)
        }
    }
}