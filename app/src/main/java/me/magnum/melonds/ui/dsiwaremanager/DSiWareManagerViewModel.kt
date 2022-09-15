package me.magnum.melonds.ui.dsiwaremanager

import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.magnum.melonds.domain.model.ConfigurationDirResult
import me.magnum.melonds.domain.model.DSiWareTitle
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.domain.services.ConfigurationDirectoryVerifier
import me.magnum.melonds.domain.services.DSiNandManager
import me.magnum.melonds.ui.dsiwaremanager.model.DSiWareManagerUiState
import me.magnum.melonds.ui.romlist.RomIcon
import java.nio.ByteBuffer
import javax.inject.Inject

@HiltViewModel
class DSiWareManagerViewModel @Inject constructor(
    private val dsiNandManager: DSiNandManager,
    private val settingsRepository: SettingsRepository,
    configurationDirectoryVerifier: ConfigurationDirectoryVerifier,
) : ViewModel() {

    private val _state = MutableStateFlow<DSiWareManagerUiState>(DSiWareManagerUiState.Loading)
    val state: StateFlow<DSiWareManagerUiState> = _state

    private val _importingTitle = MutableStateFlow(false)
    val importingTitle: StateFlow<Boolean> = _importingTitle

    init {
        val dsiConfiguration = configurationDirectoryVerifier.checkDsiConfigurationDirectory()
        if (dsiConfiguration.status != ConfigurationDirResult.Status.VALID) {
            _state.value = DSiWareManagerUiState.DSiSetupInvalid(dsiConfiguration.status)
        } else {
            viewModelScope.launch {
                withContext(Dispatchers.Default) {
                    dsiNandManager.openNand()
                    val titles = dsiNandManager.listTitles()
                    _state.value = DSiWareManagerUiState.Ready(titles)
                }
            }
        }
    }

    fun importTitleToNand(titleUri: Uri) {
        _importingTitle.value = true

        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                dsiNandManager.importTitle(titleUri)
                val titles = dsiNandManager.listTitles()
                _state.value = DSiWareManagerUiState.Ready(titles)
                _importingTitle.value = false
            }
        }
    }

    fun deleteTitle(title: DSiWareTitle) {
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                dsiNandManager.deleteTitle(title)
                val titles = dsiNandManager.listTitles()
                _state.value = DSiWareManagerUiState.Ready(titles)
            }
        }
    }

    fun getTitleIcon(title: DSiWareTitle): RomIcon {
        val bitmap = createBitmap(32, 32).apply {
            copyPixelsFromBuffer(ByteBuffer.wrap(title.icon))
        }
        val iconFiltering = settingsRepository.getRomIconFiltering()
        return RomIcon(bitmap, iconFiltering)
    }

    override fun onCleared() {
        super.onCleared()
        dsiNandManager.closeNand()
    }
}