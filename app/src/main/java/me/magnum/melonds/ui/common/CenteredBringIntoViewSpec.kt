package me.magnum.melonds.ui.common

import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * [BringIntoViewSpec] implementation that always centers the focused item in the viewport. Useful for horizontal lists where the focused item should remain centered during
 * keyboard/controller navigation.
 */
class CenteredBringIntoViewSpec : BringIntoViewSpec {

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val itemCenter = offset + size / 2
        val viewportCenter = containerSize / 2
        return itemCenter - viewportCenter
    }
}
