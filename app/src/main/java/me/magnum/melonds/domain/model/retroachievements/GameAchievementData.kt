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
        DISABLED_LOAD_ERROR,
        DISABLED_GAME_NOT_FOUND,
        ENABLED_NO_ACHIEVEMENTS,
        ENABLED_FULL,
    }

    val unlockedAchievementCount get() = totalAchievementCount - lockedAchievements.size

    val hasAchievements get() = totalAchievementCount > 0

    val isRetroAchievementsIntegrationEnabled: Boolean get() {
        return retroAchievementsIntegrationStatus == IntegrationStatus.ENABLED_FULL || retroAchievementsIntegrationStatus == IntegrationStatus.ENABLED_NO_ACHIEVEMENTS
    }

    companion object {
        fun withFullRetroAchievementsIntegration(lockedAchievements: List<RASimpleAchievement>, totalAchievementCount: Int, richPresencePatch: String?, icon: URL?): GameAchievementData {
            return GameAchievementData(
                retroAchievementsIntegrationStatus = IntegrationStatus.ENABLED_FULL,
                lockedAchievements = lockedAchievements,
                totalAchievementCount = totalAchievementCount,
                richPresencePatch = richPresencePatch,
                icon = icon,
            )
        }

        fun withLimitedRetroAchievementsIntegration(richPresencePatch: String?, icon: URL?): GameAchievementData {
            return GameAchievementData(
                retroAchievementsIntegrationStatus = IntegrationStatus.ENABLED_NO_ACHIEVEMENTS,
                lockedAchievements = emptyList(),
                totalAchievementCount = 0,
                richPresencePatch = richPresencePatch,
                icon = icon,
            )
        }

        fun withDisabledRetroAchievementsIntegration(status: IntegrationStatus, icon: URL? = null): GameAchievementData {
            require(status != IntegrationStatus.ENABLED_FULL && status != IntegrationStatus.ENABLED_NO_ACHIEVEMENTS)
            return GameAchievementData(status, emptyList(), 0, null, icon)
        }
    }
}