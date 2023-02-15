package me.magnum.melonds.ui.romdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.magnum.melonds.domain.model.retroachievements.RAUserAchievement
import me.magnum.melonds.domain.repositories.RetroAchievementsRepository
import me.magnum.melonds.parcelables.RomParcelable
import me.magnum.melonds.ui.romdetails.model.RomAchievementsSummary
import me.magnum.melonds.ui.romdetails.model.RomRetroAchievementsUiState
import javax.inject.Inject

@HiltViewModel
class RomRetroAchievementsViewModel @Inject constructor(
    private val retroAchievementsRepository: RetroAchievementsRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val rom by lazy {
        savedStateHandle.get<RomParcelable>(RomDetailsActivity.KEY_ROM)!!.rom
    }

    private val _uiState = MutableStateFlow<RomRetroAchievementsUiState>(RomRetroAchievementsUiState.Loading)
    val uiState by lazy {
        loadAchievements()
        _uiState.asStateFlow()
    }

    private fun loadAchievements() {
        viewModelScope.launch {
            if (retroAchievementsRepository.isUserAuthenticated()) {
                retroAchievementsRepository.getGameUserAchievements(rom.retroAchievementsHash).fold(
                    onSuccess = { achievements ->
                        val sortedAchievements = achievements.sortedBy {
                            // Display unlocked achievements first
                            if (it.isUnlocked) 0 else 1
                        }
                        _uiState.value = RomRetroAchievementsUiState.Ready(sortedAchievements, buildAchievementsSummary(sortedAchievements))
                    },
                    onFailure = { _uiState.value = RomRetroAchievementsUiState.AchievementLoadError },
                )
            } else {
                _uiState.value = RomRetroAchievementsUiState.LoggedOut
            }
        }
    }

    fun login(username: String, password: String) {
        _uiState.value = RomRetroAchievementsUiState.Loading
        viewModelScope.launch {
            val result = retroAchievementsRepository.login(username, password)
            if (result.isSuccess) {
                loadAchievements()
            } else {
                _uiState.value = RomRetroAchievementsUiState.LoginError
            }
        }
    }

    fun retryLoadAchievements() {
        _uiState.value = RomRetroAchievementsUiState.Loading
        loadAchievements()
    }

    private fun buildAchievementsSummary(userAchievements: List<RAUserAchievement>): RomAchievementsSummary {
        return RomAchievementsSummary(
            totalAchievements = userAchievements.size,
            completedAchievements = userAchievements.count { it.isUnlocked },
            totalPoints = userAchievements.sumOf { if (it.isUnlocked) it.achievement.points else 0 },
        )
    }
}