package me.magnum.melonds.ui.emulator.input

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.View
import android.graphics.RectF
import me.magnum.melonds.MelonEmulator.onScreenRelease
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.Point

class ExternalTouchscreenInputHandler(
    inputListener: IInputListener,
    private val viewportProvider: (() -> RectF?)? = null,
) : BaseInputHandler(inputListener) {

    private val touchPoint: Point = Point()

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                inputListener.onKeyPress(Input.TOUCHSCREEN)
                inputListener.onTouch(normalizeTouchCoordinates(event, v.width, v.height))
            }
            MotionEvent.ACTION_MOVE -> {
                inputListener.onTouch(normalizeTouchCoordinates(event, v.width, v.height))
            }
            MotionEvent.ACTION_UP -> {
                inputListener.onKeyReleased(Input.TOUCHSCREEN)
                onScreenRelease()
            }
        }
        return true
    }

    private fun normalizeTouchCoordinates(event: MotionEvent, viewWidth: Int, viewHeight: Int): Point {
        var avgX = 0f
        var avgY = 0f
        val coords = PointerCoords()

        for (i in 0 until event.pointerCount) {
            event.getPointerCoords(i, coords)
            avgX += coords.x
            avgY += coords.y
        }
        avgX /= event.pointerCount
        avgY /= event.pointerCount

        val viewport = viewportProvider?.invoke()
        if (viewport == null || viewport.width() <= 0f || viewport.height() <= 0f) {
            touchPoint.x = (avgX / viewWidth * 256f).toInt().coerceIn(0, 255)
            touchPoint.y = (avgY / viewHeight * 192f).toInt().coerceIn(0, 191)
            return touchPoint
        }

        val clampedX = ((avgX - viewport.left) / viewport.width()) * 256f
        val clampedY = ((avgY - viewport.top) / viewport.height()) * 192f

        touchPoint.x = clampedX.toInt().coerceIn(0, 255)
        touchPoint.y = clampedY.toInt().coerceIn(0, 191)

        return touchPoint
    }
}
