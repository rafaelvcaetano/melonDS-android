package me.magnum.melonds.ui.emulator.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import me.magnum.melonds.domain.model.ControllerConfiguration
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.InputConfig
import kotlin.math.absoluteValue

class InputProcessor(private val controllerConfiguration: ControllerConfiguration, private val systemInputListener: IInputListener, private val frontendInputListener: IInputListener) : INativeInputListener {

    private val axisStates: Map<Axis, AxisState>
    private val keyCombinations: List<Pair<Input, InputConfig.Assignment.Key>>
    private val pressedKeys = mutableSetOf<Int>()
    private val activeKeyCombinations = mutableMapOf<Input, InputConfig.Assignment.Key>()

    init {
        val assignments = controllerConfiguration.inputMapper.flatMap { inputConfig ->
            listOf(inputConfig.assignment, inputConfig.altAssignment)
                .map { inputConfig.input to it }
        }

        val axis = assignments.mapNotNull { (_, assignment) ->
            (assignment as? InputConfig.Assignment.Axis)?.let {
                Axis(it.deviceId, it.axisCode, it.direction)
            }
        }

        axisStates = axis.associateWith { AxisState(0f, false) }
        keyCombinations = assignments.mapNotNull { (input, assignment) ->
            (assignment as? InputConfig.Assignment.Key)
                ?.takeIf { it.modifierKeyCodes.isNotEmpty() }
                ?.let { input to it }
        }
    }

    override fun onKeyEvent(keyEvent: KeyEvent): Boolean {
        if (keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.repeatCount > 0 && activeKeyCombinationContains(keyEvent.keyCode)) {
            return true
        }

        if (keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.repeatCount == 0) {
            pressedKeys.add(keyEvent.keyCode)
            keyCombinationToInput(keyEvent.deviceId)?.let { (input, assignment) ->
                if (activeKeyCombinations[input] == null) {
                    activeKeyCombinations[input] = assignment
                    dispatchInputPressed(input)
                    return true
                }
            }
        }

        if (keyEvent.action == KeyEvent.ACTION_UP) {
            val activeCombination = activeKeyCombinations.entries.firstOrNull { (_, assignment) ->
                assignment.keyCodes.contains(keyEvent.keyCode)
            }

            if (activeCombination != null) {
                pressedKeys.remove(keyEvent.keyCode)
                activeKeyCombinations.remove(activeCombination.key)
                dispatchInputReleased(activeCombination.key)
                return true
            }

            pressedKeys.remove(keyEvent.keyCode)
        }

        val input = keyToInput(keyEvent.keyCode)
            ?: return keyIsInCombination(keyEvent.keyCode)
        when (keyEvent.action) {
            KeyEvent.ACTION_DOWN -> {
                dispatchInputPressed(input)
                return true
            }
            KeyEvent.ACTION_UP -> {
                dispatchInputReleased(input)
                return true
            }
        }
        return keyIsInCombination(keyEvent.keyCode)
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
                            dispatchInputReleased(input)
                        } else {
                            axisState.active = true
                            dispatchInputPressed(input)
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

    private fun activeKeyCombinationContains(keyCode: Int): Boolean {
        return activeKeyCombinations.values.any { it.keyCodes.contains(keyCode) }
    }

    private fun keyCombinationToInput(deviceId: Int?): Pair<Input, InputConfig.Assignment.Key>? {
        return keyCombinations
            .filter { (_, assignment) ->
                (assignment.deviceId == null || assignment.deviceId == deviceId) &&
                    pressedKeys.containsAll(assignment.keyCodes)
            }
            .maxByOrNull { (_, assignment) -> assignment.keyCodes.size }
    }

    private fun keyIsInCombination(keyCode: Int): Boolean {
        return keyCombinations.any { (_, assignment) -> assignment.keyCodes.contains(keyCode) }
    }

    private fun keyToInput(keyCode: Int): Input? {
        return controllerConfiguration.inputMapper.firstOrNull { config ->
            listOf(config.assignment, config.altAssignment).any { assignment ->
                (assignment as? InputConfig.Assignment.Key)?.let {
                    it.keyCode == keyCode && it.modifierKeyCodes.isEmpty()
                } == true
            }
        }?.input
    }

    private fun dispatchInputPressed(input: Input) {
        if (input.isSystemInput) {
            systemInputListener.onKeyPress(input)
        } else {
            frontendInputListener.onKeyPress(input)
        }
    }

    private fun dispatchInputReleased(input: Input) {
        if (input.isSystemInput) {
            systemInputListener.onKeyReleased(input)
        } else {
            frontendInputListener.onKeyReleased(input)
        }
    }
}