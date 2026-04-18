package me.magnum.melonds.ui.common.rom

import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.domain.model.emulator.validation.RomLaunchPreconditionCheckResult

sealed class RomValidationResult {
    data class Success(val rom: Rom) : RomValidationResult()
    data class PreconditionsCheckFailed(val preconditionsCheckResult: RomLaunchPreconditionCheckResult) : RomValidationResult()
}