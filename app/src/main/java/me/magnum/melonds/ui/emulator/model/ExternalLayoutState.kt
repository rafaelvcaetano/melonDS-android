package me.magnum.melonds.ui.emulator.model

import me.magnum.melonds.domain.model.Rect

data class ExternalLayoutState(
    val layoutWidth: Int,
    val layoutHeight: Int,
    val topScreen: Screen?,
    val bottomScreen: Screen?,
) {
    data class Screen(
        val rect: Rect,
        val alpha: Float,
        val onTop: Boolean,
    )
}
