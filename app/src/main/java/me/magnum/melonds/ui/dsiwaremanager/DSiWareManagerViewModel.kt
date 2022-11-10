package me.magnum.melonds.ui.dsiwaremanager

import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.magnum.melonds.domain.model.ConfigurationDirResult
import me.magnum.melonds.domain.model.DSiWareTitle
import me.magnum.melonds.domain.model.dsinand.ImportDSiWareTitleResult
import me.magnum.melonds.domain.model.dsinand.OpenDSiNandResult
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
    private val configurationDirectoryVerifier: ConfigurationDirectoryVerifier,
) : ViewModel() {

    private val _state = MutableStateFlow<DSiWareManagerUiState>(DSiWareManagerUiState.Loading)
    val state: StateFlow<DSiWareManagerUiState> = _state

    private val _importingTitle = MutableStateFlow(false)
    val importingTitle: StateFlow<Boolean> = _importingTitle.asStateFlow()

    private val _importTitleError = MutableSharedFlow<ImportDSiWareTitleResult>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val importTitleError: SharedFlow<ImportDSiWareTitleResult> = _importTitleError.asSharedFlow()

    init {
        loadDSiWareData()
    }

    fun importTitleToNand(titleUri: Uri) {
        _importingTitle.value = true

        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                val result = dsiNandManager.importTitle(titleUri)
                if (result == ImportDSiWareTitleResult.SUCCESS) {
                    val titles = dsiNandManager.listTitles()
                    _state.value = DSiWareManagerUiState.Ready(titles)
                } else {
                    _importTitleError.tryEmit(result)
                }
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

    fun revalidateBiosConfiguration() {
        _state.value = DSiWareManagerUiState.Loading
        loadDSiWareData()
    }

    private fun loadDSiWareData() {
        val dsiConfiguration = configurationDirectoryVerifier.checkDsiConfigurationDirectory()
        if (dsiConfiguration.status != ConfigurationDirResult.Status.VALID) {
            _state.value = DSiWareManagerUiState.DSiSetupInvalid(dsiConfiguration.status)
        } else {
            viewModelScope.launch {
                withContext(Dispatchers.Default) {
                    val openNandResult = dsiNandManager.openNand()
                    if (openNandResult == OpenDSiNandResult.SUCCESS) {
                        val titles = dsiNandManager.listTitles()
                        withContext(Dispatchers.Main) {
                            _state.value = DSiWareManagerUiState.Ready(titles)
                        }
                    } else {
                        // All pre-requirements are validate beforehand and no unexpected error should occur. As such,
                        // there's no point in providing a detailed description of the error to the UI.
                        withContext(Dispatchers.Main) {
                            _state.value = DSiWareManagerUiState.Error
                        }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        dsiNandManager.closeNand()
    }
}