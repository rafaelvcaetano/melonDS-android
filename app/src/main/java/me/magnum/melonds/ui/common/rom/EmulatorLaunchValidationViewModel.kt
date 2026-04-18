package me.magnum.melonds.ui.common.rom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.emulator.validation.RomLaunchPreconditionCheckResult
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.domain.services.EmulatorLaunchPreconditionChecker
import me.magnum.melonds.ui.common.rom.model.LaunchValidationResult
import me.magnum.melonds.utils.EventSharedFlow
import javax.inject.Inject

@HiltViewModel
class EmulatorLaunchValidationViewModel @Inject constructor(
    private val emulatorLaunchPreconditionChecker: EmulatorLaunchPreconditionChecker,
) : ViewModel() {

    private sealed class LaunchValidationState {
        data class ValidatingRom(val rom: Rom) : LaunchValidationState()
        data class ValidatingFirmware(val consoleType: ConsoleType) : LaunchValidationState()
    }

    private val _romValidationResult = EventSharedFlow<LaunchValidationResult>()
    val romValidationResult: Flow<LaunchValidationResult> = _romValidationResult.asSharedFlow()

    private var currentLaunchValidationState: LaunchValidationState? = null

    fun validateRomForLaunch(rom: Rom) {
        currentLaunchValidationState = LaunchValidationState.ValidatingRom(rom)

        viewModelScope.launch {
            val preconditionsCheckResult = emulatorLaunchPreconditionChecker.checkRomLaunchPreconditions(rom)
            _romValidationResult.tryEmit(LaunchValidationResult.Rom(preconditionsCheckResult))
            if (preconditionsCheckResult is RomLaunchPreconditionCheckResult.Success) {
                currentLaunchValidationState = null
            }
        }
    }

    fun validateFirmwareForLaunch(consoleType: ConsoleType) {
        currentLaunchValidationState = LaunchValidationState.ValidatingFirmware(consoleType)

        val preconditionsCheckResult = emulatorLaunchPreconditionChecker.checkFirmwareLaunchPreconditions(consoleType)
        _romValidationResult.tryEmit(LaunchValidationResult.Firmware(preconditionsCheckResult))
    }

    fun onReturnFromFirmwareSettings() {
        retryLaunchValidation()
    }

    fun onReturnFromDsiWareManagerSetup() {
        retryLaunchValidation()
    }

    private fun retryLaunchValidation() {
        currentLaunchValidationState?.let {
            when (it) {
                is LaunchValidationState.ValidatingRom -> validateRomForLaunch(it.rom)
                is LaunchValidationState.ValidatingFirmware -> validateFirmwareForLaunch(it.consoleType)
            }
        }
    }
}