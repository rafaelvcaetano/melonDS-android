package me.magnum.melonds.ui.common.rom.model

import me.magnum.melonds.domain.model.emulator.validation.FirmwareLaunchPreconditionCheckResult
import me.magnum.melonds.domain.model.emulator.validation.RomLaunchPreconditionCheckResult

sealed class LaunchValidationResult {
    data class Rom(val result: RomLaunchPreconditionCheckResult) : LaunchValidationResult()
    data class Firmware(val result: FirmwareLaunchPreconditionCheckResult) : LaunchValidationResult()
}