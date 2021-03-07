package me.magnum.melonds.ui.emulator.input

import me.magnum.melonds.domain.model.Input

class ButtonsInputHandler(inputListener: IInputListener, enableHapticFeedback: Boolean) : MultiButtonInputHandler(inputListener, enableHapticFeedback) {
    override fun getTopInput() = Input.X
    override fun getLeftInput() = Input.Y
    override fun getBottomInput() = Input.B
    override fun getRightInput() = Input.A
}