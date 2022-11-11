package me.magnum.melonds.ui.dsiwaremanager.model

import me.magnum.melonds.domain.model.Rom

sealed class DSiWareMangerRomListUiState {
    object Loading : DSiWareMangerRomListUiState()
    class Loaded(val roms: List<Rom>) : DSiWareMangerRomListUiState()
    object Empty : DSiWareMangerRomListUiState()
}