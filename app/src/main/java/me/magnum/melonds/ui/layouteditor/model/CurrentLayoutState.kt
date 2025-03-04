package me.magnum.melonds.ui.layouteditor.model

import me.magnum.melonds.domain.model.layout.LayoutConfiguration
import me.magnum.melonds.domain.model.layout.UILayout

data class CurrentLayoutState(
    val layout: UILayout,
    val orientation: LayoutConfiguration.LayoutOrientation,
)