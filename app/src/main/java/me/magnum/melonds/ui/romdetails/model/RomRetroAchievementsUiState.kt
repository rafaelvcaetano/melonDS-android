package me.magnum.melonds.ui.romdetails.model

sealed class RomRetroAchievementsUiState {
    object LoggedOut : RomRetroAchievementsUiState()
    object Loading : RomRetroAchievementsUiState()
    data class Ready(val sets: List<AchievementSetUiModel>) : RomRetroAchievementsUiState() {

        fun hasAchievements(): Boolean {
            return sets.any { it.buckets.isNotEmpty() }
        }
    }
    object LoginError : RomRetroAchievementsUiState()
    object AchievementLoadError : RomRetroAchievementsUiState()
}