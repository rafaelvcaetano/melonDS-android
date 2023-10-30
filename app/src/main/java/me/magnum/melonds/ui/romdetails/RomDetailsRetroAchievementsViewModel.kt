package me.magnum.melonds.ui.romdetails

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import me.magnum.melonds.domain.model.Rom
import me.magnum.melonds.domain.repositories.RetroAchievementsRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.parcelables.RomParcelable
import me.magnum.melonds.ui.common.viewmodel.RetroAchievementsViewModel
import javax.inject.Inject

@HiltViewModel
class RomDetailsRetroAchievementsViewModel @Inject constructor(
    retroAchievementsRepository: RetroAchievementsRepository,
    settingsRepository: SettingsRepository,
    private val savedStateHandle: SavedStateHandle,
) : RetroAchievementsViewModel(retroAchievementsRepository, settingsRepository) {

    override fun getRom(): Rom {
        return savedStateHandle.get<RomParcelable>(RomDetailsActivity.KEY_ROM)!!.rom
    }
}