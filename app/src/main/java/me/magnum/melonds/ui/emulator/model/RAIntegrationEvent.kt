package me.magnum.melonds.ui.emulator.model

import java.net.URL

sealed class RAIntegrationEvent(open val icon: URL?) {
    data class Loaded(override val icon: URL?, val unlockedAchievements: Int, val totalAchievements: Int) : RAIntegrationEvent(icon)
    data class LoadedNoAchievements(override val icon: URL?) : RAIntegrationEvent(icon)
    data class Failed(override val icon: URL?) : RAIntegrationEvent(icon)
}