package me.magnum.melonds.ui.emulator

import android.os.Bundle
import io.reactivex.Completable
import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.EmulatorConfiguration

class FirmwareEmulatorDelegate(activity: EmulatorActivity) : EmulatorDelegate(activity) {
    private enum class FirmwarePauseMenuOptions(override val textResource: Int) : EmulatorActivity.PauseMenuOption {
        SETTINGS(R.string.settings),
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
            val emulatorConfiguration = activity.viewModel.getEmulatorConfigurationForFirmware(firmwareConsoleType)
            MelonEmulator.setupEmulator(emulatorConfiguration, activity.assets)

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
            FirmwarePauseMenuOptions.EXIT -> activity.finish()
        }
    }
}