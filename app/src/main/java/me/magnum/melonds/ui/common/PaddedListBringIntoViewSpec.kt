package me.magnum.melonds.ui.common

import androidx.compose.foundation.gestures.BringIntoViewSpec
import kotlin.math.abs

/**
 * [BringIntoViewSpec] implementation that takes content padding into account. This means that focused items inside of a list are brought into view inside the useful list area
 * instead of leaving them on the edge, within the content padding area. The implementation was adapted from the default implementation of [BringIntoViewSpec].
 */
class PaddedListBringIntoViewSpec(
    private val leadingPadding: Float = 0f,
    private val trailingPadding: Float = 0f,
) : BringIntoViewSpec {

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val trailingEdge = offset + size
        val leadingEdge = offset
        return when {

            // If the item is already visible, no need to scroll.
            leadingEdge >= leadingPadding && trailingEdge <= containerSize - trailingPadding -> 0f

            // If the item is visible but larger than the parent, we don't scroll.
            leadingEdge < leadingPadding && trailingEdge > containerSize - trailingPadding -> 0f

            // Find the minimum scroll needed to make one of the edges coincide with the parent's edge.
            abs(leadingEdge + leadingPadding) < abs(trailingEdge - (containerSize - trailingPadding)) -> leadingEdge - leadingPadding
            else -> trailingEdge - containerSize + trailingPadding
        }
    }
}