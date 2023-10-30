package me.magnum.melonds.ui.emulator.model

import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.Rom

sealed class EmulatorState {
    data object Uninitialized : EmulatorState()
    data object LoadingRom : EmulatorState()
    data object LoadingFirmware : EmulatorState()
    data class RunningRom(val rom: Rom) : EmulatorState()
    data class RunningFirmware(val console: ConsoleType) : EmulatorState()
    data object RomLoadError : EmulatorState()
    data class FirmwareLoadError(val reason: MelonEmulator.FirmwareLoadResult) : EmulatorState()
    data class RomNotFoundError(val romPath: String) : EmulatorState()

    fun isRunning() = this is RunningRom || this is RunningFirmware

    fun isLoading() = this is LoadingRom || this is LoadingFirmware
}