package me.magnum.melonds.ui.emulator.input

import me.magnum.melonds.common.vibration.TouchVibrator

abstract class FeedbackInputHandler(inputListener: IInputListener, private val enableHapticFeedback: Boolean, private val touchVibrator: TouchVibrator) : BaseInputHandler(inputListener) {
    protected fun performHapticFeedback() {
        if (enableHapticFeedback) {
            touchVibrator.performTouchHapticFeedback()
        }
    }
}