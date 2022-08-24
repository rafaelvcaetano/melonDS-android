package me.magnum.melonds.ui.emulator.input

import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.Point

abstract class FrontendInputHandler : IInputListener {
    override fun onKeyPress(key: Input) {
        when (key) {
            Input.PAUSE -> onPausePressed()
            Input.FAST_FORWARD -> onFastForwardPressed()
            Input.TOGGLE_SOFT_INPUT -> onSoftInputTogglePressed()
            Input.RESET -> onResetPressed()
            Input.SWAP_SCREENS -> onSwapScreens()
            Input.QUICK_SAVE -> onQuickSave()
            Input.QUICK_LOAD -> onQuickLoad()
            Input.REWIND -> onRewind()
            else -> {}
        }
    }

    override fun onKeyReleased(key: Input) {
    }

    override fun onTouch(point: Point) {
    }

    abstract fun onPausePressed()
    abstract fun onFastForwardPressed()
    abstract fun onSoftInputTogglePressed()
    abstract fun onResetPressed()
    abstract fun onSwapScreens()
    abstract fun onQuickSave()
    abstract fun onQuickLoad()
    abstract fun onRewind()
}