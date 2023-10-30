package me.magnum.melonds.ui.emulator

import dagger.hilt.android.lifecycle.HiltViewModel
import me.magnum.melonds.domain.model.Rom
import me.magnum.melonds.domain.repositories.RetroAchievementsRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.impl.emulator.EmulatorSession
import me.magnum.melonds.ui.common.viewmodel.RetroAchievementsViewModel
import javax.inject.Inject

@HiltViewModel
class EmulatorRetroAchievementsViewModel @Inject constructor(
    retroAchievementsRepository: RetroAchievementsRepository,
    settingsRepository: SettingsRepository,
    private val emulatorSession: EmulatorSession,
) : RetroAchievementsViewModel(retroAchievementsRepository, settingsRepository) {

    override fun getRom(): Rom {
        val romSession = emulatorSession.currentSessionType() as? EmulatorSession.SessionType.RomSession
        if (romSession == null) {
            error("Emulator must be running a ROM session")
        }

        return romSession.rom
    }
}