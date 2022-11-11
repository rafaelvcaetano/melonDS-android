package me.magnum.melonds.ui.dsiwaremanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import me.magnum.melonds.domain.model.Rom
import me.magnum.melonds.domain.repositories.RomsRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.impl.RomIconProvider
import me.magnum.melonds.ui.dsiwaremanager.model.DSiWareMangerRomListUiState
import me.magnum.melonds.ui.romlist.RomIcon
import javax.inject.Inject

@HiltViewModel
class DSiWareRomListViewModel @Inject constructor(
    romsRepository: RomsRepository,
    private val settingsRepository: SettingsRepository,
    private val romIconProvider: RomIconProvider,
) : ViewModel() {

    private val _dsiWareRoms = MutableStateFlow<DSiWareMangerRomListUiState>(DSiWareMangerRomListUiState.Loading)
    val dsiWareRoms = _dsiWareRoms.asStateFlow()

    val romScanningStatus = romsRepository.getRomScanningStatus()

    init {
        romsRepository.getRoms()
            .map { roms -> roms.filter { it.isDsiWareTitle } }
            .onEach {
                if (it.isEmpty()) {
                    _dsiWareRoms.value = DSiWareMangerRomListUiState.Empty
                } else {
                    _dsiWareRoms.value = DSiWareMangerRomListUiState.Loaded(it)
                }
            }
            .launchIn(viewModelScope)
    }

    suspend fun getRomIcon(rom: Rom): RomIcon {
        val romIconBitmap = romIconProvider.getRomIcon(rom)
        val iconFiltering = settingsRepository.getRomIconFiltering()
        return RomIcon(romIconBitmap, iconFiltering)
    }
}