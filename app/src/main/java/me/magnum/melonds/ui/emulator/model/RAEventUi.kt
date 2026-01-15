package me.magnum.melonds.ui.emulator.model

import me.magnum.rcheevosapi.model.RAAchievement
import java.net.URL
import kotlin.time.Duration

sealed class RAEventUi {
    object Reset : RAEventUi()
    data class AchievementTriggered(val achievement: RAAchievement) : RAEventUi()
    data class AchievementPrimed(val achievement: RAAchievement) : RAEventUi()
    data class AchievementUnPrimed(val achievement: RAAchievement) : RAEventUi()
    data class AchievementProgressUpdated(val achievement: RAAchievement, val current: Int, val target: Int, val progress: String) : RAEventUi()
    data class GameMastered(
        val gameTitle: String,
        val gameIcon: URL,
        val userName: String?,
        val playTime: Duration?,
        val forHardcodeMode: Boolean,
    ) : RAEventUi()
}