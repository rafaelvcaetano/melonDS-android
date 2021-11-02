package me.magnum.melonds.ui.inputsetup

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import me.magnum.melonds.domain.model.ControllerConfiguration
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.InputConfig
import me.magnum.melonds.domain.repositories.SettingsRepository
import javax.inject.Inject

@HiltViewModel
class InputSetupViewModel @Inject constructor(private val settingsRepository: SettingsRepository) : ViewModel() {
    private val inputConfigLiveData: MutableLiveData<List<StatefulInputConfig>>

    init {
        val currentConfiguration = settingsRepository.getControllerConfiguration()
        val currentInputs = currentConfiguration.inputMapper.map {
            StatefulInputConfig(it.copy())
        }

        inputConfigLiveData = MutableLiveData(currentInputs)
    }

    fun getInputConfig(): LiveData<List<StatefulInputConfig>> {
        return inputConfigLiveData
    }

    fun getNextInputToConfigure(currentInput: Input): Input? {
        val currentConfig = inputConfigLiveData.value ?: return null
        val inputIndex = currentConfig.indexOfFirst { it.inputConfig.input == currentInput }
        return if (inputIndex < currentConfig.lastIndex) {
            currentConfig[inputIndex + 1].inputConfig.input
        } else {
            null
        }
    }

    fun startUpdatingInputConfig(input: Input) {
        val newConfig = inputConfigLiveData.value?.toMutableList() ?: return
        val inputIndex = newConfig.indexOfFirst { it.inputConfig.input == input }
        if (inputIndex >= 0) {
            newConfig[inputIndex] = newConfig[inputIndex].copy(isBeingConfigured = true)
            onConfigsChanged(newConfig)
        }
    }

    fun stopUpdatingInputConfig(input: Input) {
        val newConfig = inputConfigLiveData.value?.toMutableList() ?: return
        val inputIndex = newConfig.indexOfFirst { it.inputConfig.input == input }
        if (inputIndex >= 0) {
            newConfig[inputIndex] = newConfig[inputIndex].copy(isBeingConfigured = false)
            onConfigsChanged(newConfig)
        }
    }

    fun updateInputConfig(input: Input, key: Int) {
        val newConfig = inputConfigLiveData.value?.toMutableList() ?: return
        val inputIndex = newConfig.indexOfFirst { it.inputConfig.input == input }
        if (inputIndex >= 0) {
            val oldInputConfig = newConfig[inputIndex]
            newConfig[inputIndex] = oldInputConfig.copy(inputConfig = oldInputConfig.inputConfig.copy(key = key), isBeingConfigured = false)
            onConfigsChanged(newConfig)
        }
    }

    fun clearInput(input: Input) {
        updateInputConfig(input, InputConfig.KEY_NOT_SET)
    }

    private fun onConfigsChanged(newConfig: List<StatefulInputConfig>) {
        inputConfigLiveData.value = newConfig
        val currentConfiguration = buildCurrentControllerConfiguration()
        settingsRepository.setControllerConfiguration(currentConfiguration)
    }

    private fun buildCurrentControllerConfiguration(): ControllerConfiguration {
        val currentInputs = inputConfigLiveData.value!!
        val configs = currentInputs.map {
            it.inputConfig.copy()
        }

        return ControllerConfiguration(configs)
    }
}