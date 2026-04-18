package me.magnum.melonds.ui.emulator.model

import me.magnum.melonds.domain.model.input.SoftInputBehaviour
import me.magnum.melonds.domain.model.layout.LayoutConfiguration
import me.magnum.melonds.domain.model.layout.UILayout

data class RuntimeInputLayoutConfiguration(
    val softInputBehaviour: SoftInputBehaviour,
    val softInputOpacity: Int,
    val isHapticFeedbackEnabled: Boolean,
    val layoutOrientation: LayoutConfiguration.LayoutOrientation,
    val layout: UILayout,
)