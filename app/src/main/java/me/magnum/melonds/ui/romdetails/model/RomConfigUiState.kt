package me.magnum.melonds.ui.romdetails.model

sealed class RomConfigUiState {
    object Loading : RomConfigUiState()
    data class Ready(val romConfigUiModel: RomConfigUiModel) : RomConfigUiState()
}
