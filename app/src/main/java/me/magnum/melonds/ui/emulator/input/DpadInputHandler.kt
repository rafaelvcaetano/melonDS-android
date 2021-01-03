package me.magnum.melonds.ui.emulator.input

import android.view.MotionEvent
import android.view.View
import me.magnum.melonds.domain.model.Input

class DpadInputHandler(inputListener: IInputListener) : BaseInputHandler(inputListener) {
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val relativeX = event.x / v.width
        val relativeY = event.y / v.height

        inputListener.onKeyReleased(Input.RIGHT)
        inputListener.onKeyReleased(Input.LEFT)
        inputListener.onKeyReleased(Input.UP)
        inputListener.onKeyReleased(Input.DOWN)

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> if (relativeX < 0.32f) {
                if (relativeY < 0.32f) {
                    inputListener.onKeyPress(Input.UP)
                    inputListener.onKeyPress(Input.LEFT)
                } else if (relativeY < 0.68f) {
                    inputListener.onKeyPress(Input.LEFT)
                } else {
                    inputListener.onKeyPress(Input.DOWN)
                    inputListener.onKeyPress(Input.LEFT)
                }
            } else if (relativeX < 0.68f) {
                if (relativeY < 0.32f) {
                    inputListener.onKeyPress(Input.UP)
                } else if (relativeY > 0.68f) {
                    inputListener.onKeyPress(Input.DOWN)
                }
            } else {
                if (relativeY < 0.32f) {
                    inputListener.onKeyPress(Input.UP)
                    inputListener.onKeyPress(Input.RIGHT)
                } else if (relativeY < 0.68f) {
                    inputListener.onKeyPress(Input.RIGHT)
                } else {
                    inputListener.onKeyPress(Input.DOWN)
                    inputListener.onKeyPress(Input.RIGHT)
                }
            }
        }
        return true
    }
}