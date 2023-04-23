package me.magnum.melonds.ui.emulator.model

import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.Rom

sealed class EmulatorState {
    object Uninitialized : EmulatorState()
    object LoadingRom : EmulatorState()
    object LoadingFirmware : EmulatorState()
    data class RunningRom(val rom: Rom) : EmulatorState()
    data class RunningFirmware(val console: ConsoleType) : EmulatorState()
    object RomLoadError : EmulatorState()
    data class FirmwareLoadError(val reason: MelonEmulator.FirmwareLoadResult) : EmulatorState()
    object RomNotFoundError : EmulatorState()

    fun isRunning() = this is RunningRom || this is RunningFirmware
}