package me.magnum.melonds.ui.common.achievements.ui.model

import me.magnum.melonds.domain.model.retroachievements.RARuntimeUserAchievement
import me.magnum.melonds.domain.model.retroachievements.RAUserAchievement
import me.magnum.rcheevosapi.model.RAAchievement

sealed class AchievementUiModel {

    data class PrimedAchievementUiModel(
        val achievement: RAAchievement,
    ) : AchievementUiModel()

    data class UserAchievementUiModel(
        val userAchievement: RAUserAchievement,
    ) : AchievementUiModel()

    data class RuntimeAchievementUiModel(
        val runtimeAchievement: RARuntimeUserAchievement,
    ) : AchievementUiModel() {

        fun hasProgress() = runtimeAchievement.hasProgress()
    }

    fun actualAchievement(): RAAchievement = when (this) {
        is PrimedAchievementUiModel -> achievement
        is UserAchievementUiModel -> userAchievement.achievement
        is RuntimeAchievementUiModel -> runtimeAchievement.userAchievement.achievement
    }
}