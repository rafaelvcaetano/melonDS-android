package me.magnum.melonds.ui.emulator.input

import android.view.HapticFeedbackConstants
import android.view.View
import android.view.View.OnTouchListener

abstract class BaseInputHandler(protected var inputListener: IInputListener) : OnTouchListener {
    protected fun performHapticFeedback(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
    }
}