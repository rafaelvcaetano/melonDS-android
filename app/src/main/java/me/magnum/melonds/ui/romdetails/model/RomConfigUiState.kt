package me.magnum.melonds.ui.romdetails.model

import me.magnum.melonds.domain.model.RomInfo

sealed class RomConfigUiState {
    object Loading : RomConfigUiState()
    data class Ready(
        val romConfigUiModel: RomConfigUiModel,
        val romInfo: RomInfo? = null,
    ) : RomConfigUiState()
}
