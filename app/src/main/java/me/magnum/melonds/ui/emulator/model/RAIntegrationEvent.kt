package me.magnum.melonds.ui.emulator.model

sealed class RAIntegrationEvent {
    data class Loaded(val unlockedAchievements: Int, val totalAchievements: Int) : RAIntegrationEvent()
    object Failed : RAIntegrationEvent()
}