package me.magnum.melonds.ui.emulator.model

import me.magnum.melonds.domain.model.DsExternalScreen

data class ExternalDisplayConfiguration(
    val displayMode: DsExternalScreen = DsExternalScreen.TOP,
    val rotateLeft: Boolean = false,
    val keepAspectRatio: Boolean = true,
)