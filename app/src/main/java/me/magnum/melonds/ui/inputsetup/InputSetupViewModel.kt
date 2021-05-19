package me.magnum.melonds.ui.inputsetup

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.BehaviorSubject
import me.magnum.melonds.domain.model.ControllerConfiguration
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.InputConfig
import me.magnum.melonds.domain.repositories.SettingsRepository
import java.util.*
import javax.inject.Inject

@HiltViewModel
class InputSetupViewModel @Inject constructor(private val settingsRepository: SettingsRepository) : ViewModel() {
    private val inputConfigs: ArrayList<StatefulInputConfig>
    private val inputConfigsBehaviour: BehaviorSubject<List<StatefulInputConfig>>
    private val disposables: CompositeDisposable

    init {
        val currentConfiguration = settingsRepository.getControllerConfiguration()
        inputConfigs = ArrayList()
        for (config in currentConfiguration.inputMapper) {
            inputConfigs.add(StatefulInputConfig(config.copy()))
        }
        inputConfigsBehaviour = BehaviorSubject.createDefault(inputConfigs as List<StatefulInputConfig>)
        disposables = CompositeDisposable()
    }

    fun getInputConfig(): LiveData<List<StatefulInputConfig>> {
            val inputConfigLiveData = MutableLiveData<List<StatefulInputConfig>>()
            val disposable = inputConfigsBehaviour.subscribe { statefulInputConfigs -> inputConfigLiveData.value = statefulInputConfigs }
            disposables.add(disposable)
            return inputConfigLiveData
        }

    fun startUpdatingInputConfig(input: Input) {
        for (i in inputConfigs.indices) {
            val inputConfig = inputConfigs[i].inputConfig
            if (inputConfig.input === input) {
                inputConfigs[i].isBeingConfigured = true
                onConfigsChanged()
                break
            }
        }
    }

    fun stopUpdatingInputConfig(input: Input) {
        for (i in inputConfigs.indices) {
            val inputConfig = inputConfigs[i].inputConfig
            if (inputConfig.input === input) {
                inputConfigs[i].isBeingConfigured = false
                onConfigsChanged()
                break
            }
        }
    }

    fun updateInputConfig(input: Input, key: Int) {
        for (i in inputConfigs.indices) {
            val inputConfig = inputConfigs[i].inputConfig
            if (inputConfig.input === input) {
                inputConfig.key = key
                inputConfigs[i].isBeingConfigured = false
                onConfigsChanged()
                break
            }
        }
    }

    fun clearInput(input: Input) {
        updateInputConfig(input, InputConfig.KEY_NOT_SET)
    }

    private fun onConfigsChanged() {
        inputConfigsBehaviour.onNext(inputConfigs)
        val currentConfiguration = buildCurrentControllerConfiguration()
        settingsRepository.setControllerConfiguration(currentConfiguration)
    }

    private fun buildCurrentControllerConfiguration(): ControllerConfiguration {
        val configs = ArrayList<InputConfig>()
        for ((inputConfig1) in inputConfigs) {
            configs.add(inputConfig1.copy())
        }
        return ControllerConfiguration(configs)
    }

    override fun onCleared() {
        super.onCleared()
        disposables.clear()
    }
}