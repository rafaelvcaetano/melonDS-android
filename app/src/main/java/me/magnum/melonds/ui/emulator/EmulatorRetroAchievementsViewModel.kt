package me.magnum.melonds.ui.emulator

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.magnum.melonds.domain.model.retroachievements.RAEvent
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.domain.repositories.RetroAchievementsRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.domain.services.EmulatorManager
import me.magnum.melonds.extensions.removeFirst
import me.magnum.melonds.impl.emulator.EmulatorSession
import me.magnum.melonds.ui.common.viewmodel.RetroAchievementsViewModel
import me.magnum.rcheevosapi.model.RAAchievement
import javax.inject.Inject

@HiltViewModel
class EmulatorRetroAchievementsViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val retroAchievementsRepository: RetroAchievementsRepository,
    private val emulatorSession: EmulatorSession,
    private val emulatorManager: EmulatorManager,
) : RetroAchievementsViewModel(retroAchievementsRepository, settingsRepository) {

    private val _activeChallenges = MutableStateFlow<List<RAAchievement>>(emptyList())
    val activeChallenges = _activeChallenges.asStateFlow()

    init {
        viewModelScope.launch {
            emulatorManager.observeRetroAchievementEvents().collect { event ->
                when (event) {
                    is RAEvent.OnAchievementPrimed -> {
                        retroAchievementsRepository.getAchievement(event.achievementId).onSuccess { primedAchievement ->
                            if (primedAchievement != null) {
                                _activeChallenges.update {
                                    it + primedAchievement
                                }
                            }
                        }
                    }
                    is RAEvent.OnAchievementUnPrimed -> {
                        _activeChallenges.update {
                            it.toMutableList().apply {
                                removeFirst { it.id == event.achievementId }
                            }
                        }
                    }
                    else -> { /* no-op */ }
                }
            }
        }
    }

    fun onSessionReset() {
        _activeChallenges.update {
            emptyList()
        }
    }

    override fun getRom(): Rom {
        val romSession = emulatorSession.currentSessionType() as? EmulatorSession.SessionType.RomSession
        if (romSession == null) {
            error("Emulator must be running a ROM session")
        }

        return romSession.rom
    }
}