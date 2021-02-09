package me.magnum.melonds.ui.emulator.input

import android.view.MotionEvent
import android.view.View
import me.magnum.melonds.MelonEmulator.onScreenRelease
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.Point

class TouchscreenInputHandler(inputListener: IInputListener) : BaseInputHandler(inputListener) {
    private val touchPoint: Point = Point()

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                inputListener.onKeyPress(Input.TOUCHSCREEN)
                val x = event.x
                val y = event.y
                inputListener.onTouch(normalizeTouchCoordinates(x, y, v.width, v.height))
            }
            MotionEvent.ACTION_MOVE -> {
                val x = event.x
                val y = event.y
                inputListener.onTouch(normalizeTouchCoordinates(x, y, v.width, v.height))
            }
            MotionEvent.ACTION_UP -> {
                inputListener.onKeyReleased(Input.TOUCHSCREEN)
                onScreenRelease()
            }
        }
        return true
    }

    private fun normalizeTouchCoordinates(x: Float, y: Float, viewWidth: Int, viewHeight: Int): Point {
        touchPoint.x = (x / viewWidth * 256).toInt().coerceIn(0, 255)
        touchPoint.y = (y / viewHeight * 192).toInt().coerceIn(0, 191)
        return touchPoint
    }
}