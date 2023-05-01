package me.magnum.melonds.ui.romdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.magnum.melonds.domain.model.retroachievements.RAUserAchievement
import me.magnum.melonds.domain.repositories.RetroAchievementsRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.parcelables.RomParcelable
import me.magnum.melonds.ui.romdetails.model.RomAchievementsSummary
import me.magnum.melonds.ui.romdetails.model.RomRetroAchievementsUiState
import me.magnum.rcheevosapi.model.RAAchievement
import javax.inject.Inject

@HiltViewModel
class RomRetroAchievementsViewModel @Inject constructor(
    private val retroAchievementsRepository: RetroAchievementsRepository,
    private val settingsRepository: SettingsRepository,
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

    private val _viewAchievementEvent = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val viewAchievementEvent = _viewAchievementEvent.asSharedFlow()

    private fun loadAchievements() {
        viewModelScope.launch {
            if (retroAchievementsRepository.isUserAuthenticated()) {
                val forHardcoreMode = settingsRepository.isRetroAchievementsHardcoreEnabled()
                retroAchievementsRepository.getGameUserAchievements(rom.retroAchievementsHash, forHardcoreMode).fold(
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

    fun viewAchievement(achievement: RAAchievement) {
        val achievementUrl = "https://retroachievements.org/achievement/${achievement.id}"
        _viewAchievementEvent.tryEmit(achievementUrl)
    }

    private fun buildAchievementsSummary(userAchievements: List<RAUserAchievement>): RomAchievementsSummary {
        return RomAchievementsSummary(
            totalAchievements = userAchievements.size,
            completedAchievements = userAchievements.count { it.isUnlocked },
            totalPoints = userAchievements.sumOf { it.userPointsWorth() },
        )
    }
}