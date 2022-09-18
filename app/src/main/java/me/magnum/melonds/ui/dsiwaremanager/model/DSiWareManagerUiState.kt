package me.magnum.melonds.ui.dsiwaremanager.model

import me.magnum.melonds.domain.model.ConfigurationDirResult
import me.magnum.melonds.domain.model.DSiWareTitle

sealed class DSiWareManagerUiState {
    data class DSiSetupInvalid(val status: ConfigurationDirResult.Status) : DSiWareManagerUiState()
    object Loading : DSiWareManagerUiState()
    data class Ready(val titles: List<DSiWareTitle>) : DSiWareManagerUiState()
    object Error : DSiWareManagerUiState()
}