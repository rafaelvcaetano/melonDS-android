package me.magnum.melonds.ui.emulator.input

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.View
import android.graphics.RectF
import me.magnum.melonds.MelonEmulator.onScreenRelease
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.Point

class TouchscreenInputHandler(
    inputListener: IInputListener,
    private val viewRectProvider: (() -> RectF)? = null,
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
        var averageTouchX = 0f
        var averageTouchY = 0f
        val pointerCoordinates = PointerCoords()

        for (i in 0 until event.pointerCount) {
            event.getPointerCoords(i, pointerCoordinates)
            averageTouchX += pointerCoordinates.x
            averageTouchY += pointerCoordinates.y
        }
        averageTouchX /= event.pointerCount
        averageTouchY /= event.pointerCount

        val rect = viewRectProvider?.invoke()
        if (rect == null || rect.width() <= 0f || rect.height() <= 0f) {
            touchPoint.x = (averageTouchX / viewWidth * 256).toInt().coerceIn(0, 255)
            touchPoint.y = (averageTouchY / viewHeight * 192).toInt().coerceIn(0, 191)
            return touchPoint
        }

        val normalizedX = ((averageTouchX - rect.left) / rect.width() * 256f)
        val normalizedY = ((averageTouchY - rect.top) / rect.height() * 192f)

        touchPoint.x = normalizedX.toInt().coerceIn(0, 255)
        touchPoint.y = normalizedY.toInt().coerceIn(0, 191)
        return touchPoint
    }
}
