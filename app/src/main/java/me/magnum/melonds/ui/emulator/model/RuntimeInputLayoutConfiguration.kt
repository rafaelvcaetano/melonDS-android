package me.magnum.melonds.ui.emulator.model

import me.magnum.melonds.domain.model.LayoutConfiguration
import me.magnum.melonds.domain.model.UILayout

data class RuntimeInputLayoutConfiguration(
    val showSoftInput: Boolean,
    val softInputOpacity: Int,
    val isHapticFeedbackEnabled: Boolean,
    val layoutOrientation: LayoutConfiguration.LayoutOrientation,
    val layout: UILayout,
)