package me.magnum.melonds.domain.model.retroachievements

data class GameAchievementData(
    val isRetroAchievementsIntegrationEnabled: Boolean,
    val achievements: List<RASimpleAchievement>,
    val richPresencePatch: String?,
) {

    companion object {
        fun withDisabledRetroAchievementsIntegration(): GameAchievementData {
            return GameAchievementData(false, emptyList(), null)
        }
    }
}