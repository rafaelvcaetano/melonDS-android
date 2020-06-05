package me.magnum.melonds.ui.input

import android.view.MotionEvent
import android.view.View
import me.magnum.melonds.model.Input

class SingleButtonInputHandler(inputListener: IInputListener, private val input: Input) : BaseInputHandler(inputListener) {
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> inputListener.onKeyPress(input)
            MotionEvent.ACTION_UP -> inputListener.onKeyReleased(input)
        }
        return true
    }
}