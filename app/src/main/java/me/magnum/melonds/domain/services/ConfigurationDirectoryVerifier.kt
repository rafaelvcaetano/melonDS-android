package me.magnum.melonds.domain.services

import android.net.Uri
import me.magnum.melonds.domain.model.ConfigurationDirResult
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.repositories.SettingsRepository

abstract class ConfigurationDirectoryVerifier(val settingsRepository: SettingsRepository) {
    fun checkDsConfigurationDirectory(): ConfigurationDirResult {
        return checkDsConfigurationDirectory(settingsRepository.getDsBiosDirectory())
    }

    fun checkDsiConfigurationDirectory(): ConfigurationDirResult {
        return checkDsiConfigurationDirectory(settingsRepository.getDsiBiosDirectory())
    }

    abstract fun checkDsConfigurationDirectory(directory: Uri?): ConfigurationDirResult
    abstract fun checkDsiConfigurationDirectory(directory: Uri?): ConfigurationDirResult

    fun checkConsoleConfigurationDirectory(consoleType: ConsoleType): ConfigurationDirResult {
        return when (consoleType) {
            ConsoleType.DS -> checkDsConfigurationDirectory()
            ConsoleType.DSi -> checkDsiConfigurationDirectory()
        }
    }

    fun checkConsoleConfigurationDirectory(consoleType: ConsoleType, directory: Uri?): ConfigurationDirResult {
        return when (consoleType) {
            ConsoleType.DS -> checkDsConfigurationDirectory(directory)
            ConsoleType.DSi -> checkDsiConfigurationDirectory(directory)
        }
    }
}