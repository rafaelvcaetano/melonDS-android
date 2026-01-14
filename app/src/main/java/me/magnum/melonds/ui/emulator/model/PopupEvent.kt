package me.magnum.melonds.ui.emulator.model

import me.magnum.rcheevosapi.model.RAAchievement

sealed class PopupEvent {
    data class AchievementUnlockPopup(val achievement: RAAchievement) : PopupEvent()
    data class GameMasteredPopup(val event: RAEventUi.GameMastered) : PopupEvent()
    data class RAIntegrationPopup(val event: RAIntegrationEvent) : PopupEvent()
}