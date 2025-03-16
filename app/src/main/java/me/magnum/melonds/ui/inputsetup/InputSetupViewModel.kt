package me.magnum.melonds.ui.inputsetup

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.magnum.melonds.domain.model.ControllerConfiguration
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.InputConfig
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.utils.EventSharedFlow
import javax.inject.Inject

@HiltViewModel
class InputSetupViewModel @Inject constructor(private val settingsRepository: SettingsRepository) : ViewModel() {

    private val _inputConfig = MutableStateFlow(settingsRepository.getControllerConfiguration().inputMapper)
    val inputConfiguration = _inputConfig.asStateFlow()

    private val _inputUnderAssignment = MutableStateFlow<Input?>(null)
    val inputUnderAssignment = _inputUnderAssignment.asStateFlow()

    private val _onInputAssignedEvent = EventSharedFlow<Input>()
    val onInputAssignedEvent = _onInputAssignedEvent.asSharedFlow()

    fun startInputAssignment(input: Input) {
        _inputUnderAssignment.value = input
    }

    fun stopInputAssignment() {
        _inputUnderAssignment.value = null
    }

    fun updateInputAssignedKey(key: Int) {
        val inputUnderAssignment = _inputUnderAssignment.value ?: return
        val inputType = InputConfig.Assignment.Key(null, key)
        setInputAssignment(inputUnderAssignment, inputType)
        focusOnNextInput(inputUnderAssignment)
    }

    fun updateInputAssignedAxis(axis: Int, direction: InputConfig.Assignment.Axis.Direction) {
        val inputUnderAssignment = _inputUnderAssignment.value ?: return
        val inputType = InputConfig.Assignment.Axis(null, axis, direction)
        setInputAssignment(inputUnderAssignment, inputType)
        focusOnNextInput(inputUnderAssignment)
    }

    fun clearInputAssignment(input: Input) {
        setInputAssignment(input, InputConfig.Assignment.None)
        _inputUnderAssignment.value = null
    }

    private fun setInputAssignment(input: Input, assignment: InputConfig.Assignment) {
        val inputIndex = _inputConfig.value.indexOfFirst { it.input == input }
        if (inputIndex >= 0) {
            _inputConfig.update { config ->
                config.toMutableList().apply {
                    this[inputIndex] = this[inputIndex].copy(assignment = assignment)
                }.also {
                    onConfigsChanged(it)
                }
            }
        }
        _inputUnderAssignment.value = null
    }

    private fun onConfigsChanged(newConfig: List<InputConfig>) {
        val currentConfiguration = ControllerConfiguration(newConfig)
        settingsRepository.setControllerConfiguration(currentConfiguration)
    }

    private fun focusOnNextInput(currentInput: Input) {
        val currentInputIndex = _inputConfig.value.indexOfFirst { it.input == currentInput }
        val nextInput = _inputConfig.value.getOrNull(currentInputIndex + 1)
        if (nextInput != null) {
            _onInputAssignedEvent.tryEmit(nextInput.input)
        }
    }
}