package me.magnum.melonds.domain.model.emulator.validation

import me.magnum.melonds.domain.model.ConfigurationDirResult
import me.magnum.melonds.domain.model.ConsoleType

sealed class FirmwareLaunchPreconditionCheckResult {
    data class Success(val consoleType: ConsoleType) : FirmwareLaunchPreconditionCheckResult()
    data class BiosConfigurationIncorrect(val configurationDirectoryResult: ConfigurationDirResult) : FirmwareLaunchPreconditionCheckResult()
}