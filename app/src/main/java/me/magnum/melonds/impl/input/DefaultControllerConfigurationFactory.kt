package me.magnum.melonds.impl.input

import android.view.KeyEvent
import android.view.MotionEvent
import me.magnum.melonds.domain.model.ControllerConfiguration
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.InputConfig

class DefaultControllerConfigurationFactory : ControllerConfigurationFactory {

    override fun buildDefaultControllerConfiguration(): ControllerConfiguration {
        val inputList = listOf(
            InputConfig(Input.A, InputConfig.Assignment.Key(null, KeyEvent.KEYCODE_BUTTON_B)),
            InputConfig(Input.B, InputConfig.Assignment.Key(null, KeyEvent.KEYCODE_BUTTON_A)),
            InputConfig(Input.X, InputConfig.Assignment.Key(null, KeyEvent.KEYCODE_BUTTON_Y)),
            InputConfig(Input.Y, InputConfig.Assignment.Key(null, KeyEvent.KEYCODE_BUTTON_X)),
            InputConfig(Input.LEFT, InputConfig.Assignment.Axis(null, MotionEvent.AXIS_HAT_X, InputConfig.Assignment.Axis.Direction.NEGATIVE), InputConfig.Assignment.Axis(null, MotionEvent.AXIS_X, InputConfig.Assignment.Axis.Direction.NEGATIVE)),
            InputConfig(Input.RIGHT, InputConfig.Assignment.Axis(null, MotionEvent.AXIS_HAT_X, InputConfig.Assignment.Axis.Direction.POSITIVE), InputConfig.Assignment.Axis(null, MotionEvent.AXIS_X, InputConfig.Assignment.Axis.Direction.POSITIVE)),
            InputConfig(Input.UP, InputConfig.Assignment.Axis(null, MotionEvent.AXIS_HAT_Y, InputConfig.Assignment.Axis.Direction.NEGATIVE), InputConfig.Assignment.Axis(null, MotionEvent.AXIS_Y, InputConfig.Assignment.Axis.Direction.NEGATIVE)),
            InputConfig(Input.DOWN, InputConfig.Assignment.Axis(null, MotionEvent.AXIS_HAT_Y, InputConfig.Assignment.Axis.Direction.POSITIVE), InputConfig.Assignment.Axis(null, MotionEvent.AXIS_Y, InputConfig.Assignment.Axis.Direction.POSITIVE)),
            InputConfig(Input.L, InputConfig.Assignment.Key(null, KeyEvent.KEYCODE_BUTTON_L1)),
            InputConfig(Input.R, InputConfig.Assignment.Key(null, KeyEvent.KEYCODE_BUTTON_R1)),
            InputConfig(Input.START, InputConfig.Assignment.Key(null, KeyEvent.KEYCODE_BUTTON_START)),
            InputConfig(Input.SELECT, InputConfig.Assignment.Key(null, KeyEvent.KEYCODE_BUTTON_SELECT)),
            InputConfig(Input.PAUSE, InputConfig.Assignment.Key(null, KeyEvent.KEYCODE_BUTTON_MODE)),
        )

        return ControllerConfiguration(inputList)
    }
}