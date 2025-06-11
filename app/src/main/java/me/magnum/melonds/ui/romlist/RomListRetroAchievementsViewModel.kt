package me.magnum.melonds.ui.romlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.domain.model.retroachievements.RAUserAchievement
import me.magnum.melonds.domain.repositories.RetroAchievementsRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.ui.romdetails.model.RomAchievementsSummary
import me.magnum.melonds.ui.romdetails.model.RomRetroAchievementsUiState
import me.magnum.rcheevosapi.model.RAAchievement
import javax.inject.Inject

@HiltViewModel
class RomListRetroAchievementsViewModel @Inject constructor(
    private val retroAchievementsRepository: RetroAchievementsRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<RomRetroAchievementsUiState>(RomRetroAchievementsUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _viewAchievementEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val viewAchievementEvent = _viewAchievementEvent.asSharedFlow()

    private var loadJob: Job? = null
    private var currentRom: Rom? = null

    fun setRom(rom: Rom) {
        if (rom == currentRom) return
        currentRom = rom
        loadAchievements()
    }

    fun retryLoadAchievements() {
        _uiState.value = RomRetroAchievementsUiState.Loading
        loadAchievements()
    }

    fun viewAchievement(achievement: RAAchievement) {
        val url = "https://retroachievements.org/achievement/${achievement.id}"
        _viewAchievementEvent.tryEmit(url)
    }

    private fun loadAchievements() {
        val rom = currentRom ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                if (retroAchievementsRepository.isUserAuthenticated()) {
                    val hardcore = settingsRepository.isRetroAchievementsHardcoreEnabled()
                    retroAchievementsRepository.getGameUserAchievements(rom.retroAchievementsHash, hardcore).fold(
                        onSuccess = { achievements ->
                            val sorted = achievements.sortedBy { if (it.isUnlocked) 0 else 1 }
                            _uiState.value = RomRetroAchievementsUiState.Ready(sorted, buildSummary(hardcore, sorted))
                        },
                        onFailure = {
                            _uiState.value = RomRetroAchievementsUiState.AchievementLoadError
                        }
                    )
                } else {
                    _uiState.value = RomRetroAchievementsUiState.LoggedOut
                }
            } catch (_: Exception) {
                _uiState.value = RomRetroAchievementsUiState.AchievementLoadError
            }
        }
    }

    private fun buildSummary(hardcore: Boolean, list: List<RAUserAchievement>): RomAchievementsSummary {
        return RomAchievementsSummary(
            forHardcoreMode = hardcore,
            totalAchievements = list.size,
            completedAchievements = list.count { it.isUnlocked },
            totalPoints = list.sumOf { it.userPointsWorth() },
        )
    }
}
