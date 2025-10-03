package me.magnum.melonds.ui.emulator.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import me.magnum.melonds.domain.model.ControllerConfiguration
import me.magnum.melonds.domain.model.InputConfig
import kotlin.math.absoluteValue

class InputProcessor(private val controllerConfiguration: ControllerConfiguration, private val systemInputListener: IInputListener, private val frontendInputListener: IInputListener) : INativeInputListener {

    private val axisStates: Map<Axis, AxisState>

    init {
        val axis = controllerConfiguration.inputMapper.flatMap { inputConfig ->
            listOf(inputConfig.assignment, inputConfig.altAssignment)
        }.mapNotNull { assignment ->
            (assignment as? InputConfig.Assignment.Axis)?.let {
                Axis(it.deviceId, it.axisCode, it.direction)
            }
        }

        axisStates = axis.associateWith { AxisState(0f, false) }
    }

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

    override fun onMotionEvent(motionEvent: MotionEvent): Boolean {
        if (motionEvent.isFromSource(InputDevice.SOURCE_CLASS_JOYSTICK)) {
            val deviceAxis = axisStates.filterKeys { it.deviceId == null || it.deviceId == motionEvent.deviceId }
            deviceAxis.forEach {
                val axis = it.key
                val axisState = it.value

                val newValue = motionEvent.getAxisValue(axis.axisCode)
                val clampedValue = when (axis.direction) {
                    InputConfig.Assignment.Axis.Direction.POSITIVE -> newValue.coerceAtLeast(0f)
                    InputConfig.Assignment.Axis.Direction.NEGATIVE -> newValue.coerceAtMost(0f)
                }

                if (axisState.shouldToggleFor(newValue = clampedValue)) {
                    controllerConfiguration.axisToInput(axis.axisCode, axis.direction)?.let { input ->
                        if (axisState.active) {
                            axisState.active = false
                            if (input.isSystemInput) {
                                systemInputListener.onKeyReleased(input)
                            } else {
                                frontendInputListener.onKeyReleased(input)
                            }
                        } else {
                            axisState.active = true
                            if (input.isSystemInput) {
                                systemInputListener.onKeyPress(input)
                            } else {
                                frontendInputListener.onKeyPress(input)
                            }
                        }
                    }
                }
                axisState.value = clampedValue
            }
            return deviceAxis.isNotEmpty()
        } else {
            return false
        }
    }

    private data class Axis(
        val deviceId: Int?,
        val axisCode: Int,
        val direction: InputConfig.Assignment.Axis.Direction,
    )

    private data class AxisState(
        var value: Float,
        var active: Boolean,
    ) {
        fun shouldToggleFor(newValue: Float): Boolean {
            return if (active) {
                newValue.absoluteValue < 0.5f
            } else {
                newValue.absoluteValue >= 0.5f
            }
        }
    }
}