package me.magnum.melonds.ui.romdetails.model

import me.magnum.melonds.domain.model.retroachievements.RAUserAchievement

sealed class RomRetroAchievementsUiState {
    object LoggedOut : RomRetroAchievementsUiState()
    object Loading : RomRetroAchievementsUiState()
    data class Ready(val achievements: List<RAUserAchievement>) : RomRetroAchievementsUiState()
    object LoginError : RomRetroAchievementsUiState()
    object AchievementLoadError : RomRetroAchievementsUiState()
}