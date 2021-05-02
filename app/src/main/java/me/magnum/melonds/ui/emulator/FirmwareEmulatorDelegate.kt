package me.magnum.melonds.ui.emulator

import android.os.Bundle
import io.reactivex.Completable
import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.EmulatorConfiguration
import me.magnum.melonds.common.UriFileHandler

class FirmwareEmulatorDelegate(activity: EmulatorActivity) : EmulatorDelegate(activity) {
    private enum class FirmwarePauseMenuOptions(override val textResource: Int) : EmulatorActivity.PauseMenuOption {
        SETTINGS(R.string.settings),
        RESET(R.string.reset),
        EXIT(R.string.exit)
    }

    private lateinit var firmwareConsoleType: ConsoleType

    override fun getEmulatorSetupObservable(extras: Bundle?): Completable {
        val consoleTypeParameter = extras?.getInt(EmulatorActivity.KEY_BOOT_FIRMWARE_CONSOLE, -1)
        if (consoleTypeParameter == null || consoleTypeParameter == -1) {
            throw RuntimeException("No console type specified")
        }

        firmwareConsoleType = ConsoleType.values()[consoleTypeParameter]
        return Completable.create { emitter ->
            activity.viewModel.loadLayoutForFirmware()
            val emulatorConfiguration = activity.viewModel.getEmulatorConfigurationForFirmware(firmwareConsoleType)
            MelonEmulator.setupEmulator(emulatorConfiguration, activity.assets, activity.buildUriFileHandler())

            val loadResult = MelonEmulator.bootFirmware()
            if (loadResult != MelonEmulator.FirmwareLoadResult.SUCCESS)
                throw EmulatorActivity.FirmwareLoadFailedException(loadResult)

            emitter.onComplete()
        }
    }

    override fun getEmulatorConfiguration(): EmulatorConfiguration {
        return activity.viewModel.getEmulatorConfigurationForFirmware(firmwareConsoleType)
    }

    override fun getPauseMenuOptions(): List<EmulatorActivity.PauseMenuOption> {
        return FirmwarePauseMenuOptions.values().toList()
    }

    override fun onPauseMenuOptionSelected(option: EmulatorActivity.PauseMenuOption) {
        when (option) {
            FirmwarePauseMenuOptions.SETTINGS -> activity.openSettings()
            FirmwarePauseMenuOptions.RESET -> activity.resetEmulation()
            FirmwarePauseMenuOptions.EXIT -> activity.finish()
        }
    }

    override fun getCrashContext(): Any {
        return FirmwareCrashContext(getEmulatorConfiguration(), activity.viewModel.getDsBiosDirectory()?.toString(), activity.viewModel.getDsiBiosDirectory()?.toString())
    }

    override fun dispose() {
    }

    private data class FirmwareCrashContext(val emulatorConfiguration: EmulatorConfiguration, val dsiBiosDirUri: String?, val dsBiosDirUri: String?)
}