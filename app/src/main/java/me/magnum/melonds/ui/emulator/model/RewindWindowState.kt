package me.magnum.melonds.ui.emulator.model

import me.magnum.melonds.domain.model.rewind.RewindWindowPosition
import me.magnum.melonds.ui.emulator.rewind.model.RewindWindow

sealed class RewindWindowState {
    data object Hidden : RewindWindowState()
    data class Visible(
        val rewindWindow: RewindWindow,
        val windowPosition: RewindWindowPosition,
    ) : RewindWindowState()
}
