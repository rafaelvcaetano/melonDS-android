package me.magnum.melonds.domain.model.layout

import me.magnum.melonds.domain.model.Point
import me.magnum.melonds.domain.model.Rect
import kotlin.math.roundToInt

/**
 * Returns a new [UILayout] whose component rectangles are scaled from [fromSize] into [toSize].
 */
fun UILayout.scaleToSize(fromSize: Point, toSize: Point): UILayout {
    val currentComponents = components ?: return this
    if (fromSize == toSize || fromSize.x <= 0 || fromSize.y <= 0 || toSize.x <= 0 || toSize.y <= 0) {
        return this
    }

    val scaleX = toSize.x.toFloat() / fromSize.x
    val scaleY = toSize.y.toFloat() / fromSize.y

    val scaledComponents = currentComponents.map {
        val rect = it.rect
        val scaledRect = Rect(
            (rect.x * scaleX).roundToInt(),
            (rect.y * scaleY).roundToInt(),
            (rect.width * scaleX).roundToInt(),
            (rect.height * scaleY).roundToInt(),
        )
        it.copy(rect = scaledRect)
    }

    return copy(components = scaledComponents)
}
