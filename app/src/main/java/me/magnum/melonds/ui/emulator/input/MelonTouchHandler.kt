package me.magnum.melonds.ui.emulator.input

import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.Point

class MelonTouchHandler : IInputListener {
    private var isLidClosed = false

    override fun onKeyPress(key: Input) {
        if (key == Input.HINGE) {
            handleHingePress()
        } else {
            MelonEmulator.onInputDown(key)
        }
    }

    override fun onKeyReleased(key: Input) {
        if (key != Input.HINGE) {
            MelonEmulator.onInputUp(key)
        }
    }

    override fun onTouch(point: Point) {
        MelonEmulator.onScreenTouch(point.x, point.y)
    }

    fun setLidClosed(closed: Boolean) {
        if (closed != isLidClosed) {
            isLidClosed = closed
            if (isLidClosed) {
                MelonEmulator.onInputDown(Input.HINGE)
            } else {
                MelonEmulator.onInputUp(Input.HINGE)
            }
        }
    }

    private fun handleHingePress() {
        setLidClosed(!isLidClosed)
    }
}