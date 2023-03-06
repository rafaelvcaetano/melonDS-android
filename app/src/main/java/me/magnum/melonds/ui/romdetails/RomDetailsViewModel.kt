package me.magnum.melonds.ui.romdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.magnum.melonds.common.Permission
import me.magnum.melonds.common.UriPermissionManager
import me.magnum.melonds.domain.model.Rom
import me.magnum.melonds.domain.model.RomConfig
import me.magnum.melonds.domain.repositories.RomsRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.impl.RomIconProvider
import me.magnum.melonds.parcelables.RomParcelable
import me.magnum.melonds.ui.romdetails.model.RomConfigUiState
import me.magnum.melonds.ui.romdetails.model.RomConfigUpdateEvent
import me.magnum.melonds.ui.romlist.RomIcon
import javax.inject.Inject

@HiltViewModel
class RomDetailsViewModel @Inject constructor(
    private val romDetailsUiMapper: RomDetailsUiMapper,
    private val romsRepository: RomsRepository,
    private val settingsRepository: SettingsRepository,
    private val romIconProvider: RomIconProvider,
    private val uriPermissionManager: UriPermissionManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _rom = MutableStateFlow(savedStateHandle.get<RomParcelable>(RomDetailsActivity.KEY_ROM)!!.rom)
    val rom = _rom.asStateFlow()

    private val _romConfig = MutableStateFlow<RomConfigUiState>(RomConfigUiState.Loading)
    val romConfig by lazy {
        updateRomConfigState()
        _romConfig.asStateFlow()
    }

    fun onRomConfigUpdateEvent(event: RomConfigUpdateEvent) {
        val newConfig = when(event) {
            is RomConfigUpdateEvent.RuntimeConsoleUpdate -> _rom.value.config.copy(runtimeConsoleType = event.newRuntimeConsole)
            is RomConfigUpdateEvent.RuntimeMicSourceUpdate -> _rom.value.config.copy(runtimeMicSource = event.newRuntimeMicSource)
            is RomConfigUpdateEvent.LayoutUpdate -> _rom.value.config.copy(layoutId = event.newLayoutId)
            is RomConfigUpdateEvent.LoadGbaRomUpdate -> _rom.value.config.copy(loadGbaCart = event.shouldLoadRom)
            is RomConfigUpdateEvent.GbaRomPathUpdate -> _rom.value.config.copy(gbaCartPath = event.gbaRomPath)
            is RomConfigUpdateEvent.GbaSavePathUpdate -> _rom.value.config.copy(gbaSavePath = event.gbaSavePath)
        }

        _rom.update { it.copy(config = newConfig) }
        saveRomConfig(newConfig)
        updateRomConfigState()
    }

    suspend fun getRomIcon(rom: Rom): RomIcon {
        val romIconBitmap = romIconProvider.getRomIcon(rom)
        val iconFiltering = settingsRepository.getRomIconFiltering()
        return RomIcon(romIconBitmap, iconFiltering)
    }

    private fun updateRomConfigState() {
        viewModelScope.launch {
            val romConfigUiModel = romDetailsUiMapper.mapRomConfigToUi(_rom.value.config)
            _romConfig.value = RomConfigUiState.Ready(romConfigUiModel)
        }
    }

    private fun saveRomConfig(newConfig: RomConfig) {
        newConfig.gbaCartPath?.let { uriPermissionManager.persistFilePermissions(it, Permission.READ) }
        newConfig.gbaSavePath?.let { uriPermissionManager.persistFilePermissions(it, Permission.READ_WRITE) }
        romsRepository.updateRomConfig(_rom.value, newConfig)
    }
}