package me.magnum.melonds.ui.dsiwaremanager.model

import me.magnum.melonds.domain.model.DSiWareTitle

sealed class DSiWareManagerUiState {
    object Loading : DSiWareManagerUiState()
    data class Ready(val titles: List<DSiWareTitle>) : DSiWareManagerUiState()
}