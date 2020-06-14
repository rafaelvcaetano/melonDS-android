package me.magnum.melonds.ui.input

import android.view.MotionEvent
import android.view.View
import me.magnum.melonds.model.Input

class SingleButtonInputHandler(inputListener: IInputListener, private val input: Input, private val isToggle: Boolean = false) : BaseInputHandler(inputListener) {
    private var pressed = false
    private var toggleEnabled = false

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (isToggle) {
            if (event.action == MotionEvent.ACTION_DOWN && !pressed)
                pressed = true
            else if (event.action == MotionEvent.ACTION_UP && pressed) {
                toggleEnabled = !toggleEnabled
                pressed = false

                if (toggleEnabled)
                    inputListener.onKeyPress(input)
                else
                    inputListener.onKeyReleased(input)
            }
        } else {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> inputListener.onKeyPress(input)
                MotionEvent.ACTION_UP -> inputListener.onKeyReleased(input)
            }
        }
        return true
    }
}