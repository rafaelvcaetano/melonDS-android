package me.magnum.melonds.ui.emulator.model

import me.magnum.rcheevosapi.model.RAAchievement

sealed class RAEventUi {
    object Reset : RAEventUi()
    data class AchievementTriggered(val achievement: RAAchievement) : RAEventUi()
    data class AchievementPrimed(val achievement: RAAchievement) : RAEventUi()
    data class AchievementUnPrimed(val achievement: RAAchievement) : RAEventUi()
    data class AchievementProgressUpdated(val achievement: RAAchievement, val progress: String) : RAEventUi()
}