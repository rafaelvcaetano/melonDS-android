package me.magnum.melonds.ui.emulator.model

import me.magnum.melonds.domain.model.DsExternalScreen
import me.magnum.melonds.domain.model.ScreenAlignment

data class ExternalDisplayConfiguration(
    val displayMode: DsExternalScreen = DsExternalScreen.TOP,
    val rotateLeft: Boolean = false,
    val keepAspectRatio: Boolean = true,
    val integerScale: Boolean = false,
    val verticalAlignment: ScreenAlignment = ScreenAlignment.TOP,
    val fillHeight: Boolean = false,
    val fillWidth: Boolean = false,
)
