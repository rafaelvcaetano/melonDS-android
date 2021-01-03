package me.magnum.melonds.ui.input

import android.view.MotionEvent
import android.view.View
import me.magnum.melonds.domain.model.Input

class ButtonsInputHandler(inputListener: IInputListener) : BaseInputHandler(inputListener) {
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val relativeX = event.x / v.width
        val relativeY = event.y / v.height

        inputListener.onKeyReleased(Input.A)
        inputListener.onKeyReleased(Input.B)
        inputListener.onKeyReleased(Input.X)
        inputListener.onKeyReleased(Input.Y)

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (relativeX < 0.32f) {
                    if (relativeY < 0.32f) {
                        inputListener.onKeyPress(Input.X)
                        inputListener.onKeyPress(Input.Y)
                    } else if (relativeY < 0.68f) {
                        inputListener.onKeyPress(Input.Y)
                    } else {
                        inputListener.onKeyPress(Input.Y)
                        inputListener.onKeyPress(Input.B)
                    }
                } else if (relativeX < 0.68f) {
                    if (relativeY < 0.32f) {
                        inputListener.onKeyPress(Input.X)
                    } else if (relativeY > 0.68f) {
                        inputListener.onKeyPress(Input.B)
                    }
                } else {
                    if (relativeY < 0.32f) {
                        inputListener.onKeyPress(Input.X)
                        inputListener.onKeyPress(Input.A)
                    } else if (relativeY < 0.68f) {
                        inputListener.onKeyPress(Input.A)
                    } else {
                        inputListener.onKeyPress(Input.A)
                        inputListener.onKeyPress(Input.B)
                    }
                }
            }
        }
        return true
    }
}