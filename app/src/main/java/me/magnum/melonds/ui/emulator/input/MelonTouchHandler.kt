package me.magnum.melonds.ui.emulator.input

import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.Point
import me.magnum.melonds.ui.emulator.autoflute.AutoFluteController

class MelonTouchHandler(private val autoFluteController: AutoFluteController? = null) : IInputListener {
    private var isLidClosed = false
    private var isTouching = false

    override fun onKeyPress(key: Input) {
        if (key == Input.HINGE) {
            handleHingePress()
        } else {
            if (key == Input.TOUCHSCREEN) {
                // Touching the screen while the auto flute is playing takes over control from it
                autoFluteController?.takeIf { it.isPlaying }?.stop()
            }
            MelonEmulator.onInputDown(key)
        }
    }

    override fun onKeyReleased(key: Input) {
        if (key != Input.HINGE) {
            if (key == Input.TOUCHSCREEN) {
                isTouching = false
                autoFluteController?.onStylusUp()
            }
            MelonEmulator.onInputUp(key)
        }
    }

    override fun onTouch(point: Point) {
        if (isTouching) {
            autoFluteController?.onStylusMove(point.x, point.y)
        } else {
            isTouching = true
            autoFluteController?.onStylusDown(point.x, point.y)
        }
        MelonEmulator.onScreenTouch(point.x, point.y)
    }

    private fun handleHingePress() {
        isLidClosed = !isLidClosed
        if (isLidClosed) {
            MelonEmulator.onInputDown(Input.HINGE)
        } else {
            MelonEmulator.onInputUp(Input.HINGE)
        }
    }
}