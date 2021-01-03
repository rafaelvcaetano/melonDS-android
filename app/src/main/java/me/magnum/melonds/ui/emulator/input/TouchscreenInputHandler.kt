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
                touchPoint.x = (x / v.width * 256).toInt()
                touchPoint.y = (y / v.height * 192).toInt()
                inputListener.onTouch(touchPoint)
            }
            MotionEvent.ACTION_MOVE -> {
                val x = event.x
                val y = event.y
                touchPoint.x = (x / v.width * 256).toInt()
                touchPoint.y = (y / v.height * 192).toInt()
                inputListener.onTouch(touchPoint)
            }
            MotionEvent.ACTION_UP -> {
                inputListener.onKeyReleased(Input.TOUCHSCREEN)
                onScreenRelease()
            }
        }
        return true
    }
}