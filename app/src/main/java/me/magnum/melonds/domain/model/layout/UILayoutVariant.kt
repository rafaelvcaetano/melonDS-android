package me.magnum.melonds.domain.model.layout

import me.magnum.melonds.domain.model.Point
import me.magnum.melonds.domain.model.ui.Orientation

data class UILayoutVariant(
    val uiSize: Point,
    val orientation: Orientation,
    val folds: List<ScreenFold>,
)