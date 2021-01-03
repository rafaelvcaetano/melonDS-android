package me.magnum.melonds.ui.input

import android.view.KeyEvent
import me.magnum.melonds.domain.model.ControllerConfiguration

class InputProcessor(private val controllerConfiguration: ControllerConfiguration, private val systemInputListener: IInputListener, private val frontendInputListener: IInputListener) : INativeInputListener {
    override fun onKeyEvent(keyEvent: KeyEvent): Boolean {
        val input = controllerConfiguration.keyToInput(keyEvent.keyCode) ?: return false
        if (input.isSystemInput) {
            when (keyEvent.action) {
                KeyEvent.ACTION_DOWN -> {
                    systemInputListener.onKeyPress(input)
                    return true
                }
                KeyEvent.ACTION_UP -> {
                    systemInputListener.onKeyReleased(input)
                    return true
                }
            }
        } else {
            when (keyEvent.action) {
                KeyEvent.ACTION_DOWN -> {
                    frontendInputListener.onKeyPress(input)
                    return true
                }
                KeyEvent.ACTION_UP -> {
                    frontendInputListener.onKeyReleased(input)
                    return true
                }
            }
        }
        return false
    }
}