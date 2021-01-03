package me.magnum.melonds.ui.input

import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.Point

abstract class FrontendInputHandler : IInputListener {
    override fun onKeyPress(key: Input) {
        when (key) {
            Input.PAUSE -> onPausePressed()
            Input.FAST_FORWARD -> onFastForwardPressed()
        }
    }

    override fun onKeyReleased(key: Input) {
    }

    override fun onTouch(point: Point) {
    }

    abstract fun onPausePressed()
    abstract fun onFastForwardPressed()
}