package me.magnum.melonds.ui.input

import me.magnum.melonds.MelonEmulator.onInputDown
import me.magnum.melonds.MelonEmulator.onInputUp
import me.magnum.melonds.MelonEmulator.onScreenTouch
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.Point

class MelonTouchHandler : IInputListener {
    override fun onKeyPress(key: Input) {
        onInputDown(key)
    }

    override fun onKeyReleased(key: Input) {
        onInputUp(key)
    }

    override fun onTouch(point: Point) {
        onScreenTouch(point.x, point.y)
    }
}