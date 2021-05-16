package me.magnum.melonds.ui.emulator.input

import android.view.MotionEvent
import android.view.View
import me.magnum.melonds.domain.model.Input

class SingleButtonInputHandler(inputListener: IInputListener, private val input: Input, private val enableHapticFeedback: Boolean) : BaseInputHandler(inputListener) {
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                inputListener.onKeyPress(input)
                if (enableHapticFeedback) {
                    performHapticFeedback(v)
                }
            }
            MotionEvent.ACTION_UP -> inputListener.onKeyReleased(input)
        }
        return true
    }
}