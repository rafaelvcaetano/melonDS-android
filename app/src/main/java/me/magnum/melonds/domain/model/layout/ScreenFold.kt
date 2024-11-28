package me.magnum.melonds.domain.model.layout

import me.magnum.melonds.domain.model.Rect
import me.magnum.melonds.domain.model.ui.Orientation

data class ScreenFold(
    val orientation: Orientation,
    val type: FoldType,
    val foldBounds: Rect,
) {
    
    enum class FoldType {
        SEAMLESS,
        GAP,
    }
}