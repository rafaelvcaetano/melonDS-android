package me.magnum.melonds.domain.model.retroachievements

import java.net.URL

class GameAchievementData private constructor(
    val retroAchievementsIntegrationStatus: IntegrationStatus,
    val lockedAchievements: List<RASimpleAchievement>,
    val totalAchievementCount: Int,
    val richPresencePatch: String?,
    val icon: URL?,
) {

    enum class IntegrationStatus {
        DISABLED_NOT_LOGGED_IN,
        DISABLED_NO_ACHIEVEMENTS,
        DISABLED_LOAD_ERROR,
        ENABLED,
    }

    val unlockedAchievementCount get() = totalAchievementCount - lockedAchievements.size

    companion object {
        fun withRetroAchievementsIntegration(lockedAchievements: List<RASimpleAchievement>, totalAchievementCount: Int, richPresencePatch: String?, icon: URL?): GameAchievementData {
            return GameAchievementData(
                retroAchievementsIntegrationStatus = IntegrationStatus.ENABLED,
                lockedAchievements = lockedAchievements,
                totalAchievementCount = totalAchievementCount,
                richPresencePatch = richPresencePatch,
                icon = icon,
            )
        }

        fun withDisabledRetroAchievementsIntegration(status: IntegrationStatus, icon: URL? = null): GameAchievementData {
            require(status != IntegrationStatus.ENABLED)
            return GameAchievementData(status, emptyList(), 0, null, icon)
        }
    }
}