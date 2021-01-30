package me.magnum.melonds.ui.emulator

import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.Completable
import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.EmulatorConfiguration

@AndroidEntryPoint
class FirmwareEmulatorActivity : EmulatorActivity() {
    companion object {
        const val KEY_BOOT_FIRMWARE_CONSOLE = "boot_firmware_console"
    }

    private enum class FirmwarePauseMenuOptions(override val textResource: Int) : PauseMenuOption {
        SETTINGS(R.string.settings),
        EXIT(R.string.exit)
    }

    private lateinit var firmwareConsoleType: ConsoleType

    override fun getEmulatorSetupObservable(): Completable {
        val consoleTypeParameter = intent.extras?.getInt(KEY_BOOT_FIRMWARE_CONSOLE, -1)
        if (consoleTypeParameter == null || consoleTypeParameter == -1) {
            throw RuntimeException("No console type specified")
        }

        firmwareConsoleType = ConsoleType.values()[consoleTypeParameter]
        return Completable.create { emitter ->
            val emulatorConfiguration = viewModel.getEmulatorConfigurationForFirmware(firmwareConsoleType)
            MelonEmulator.setupEmulator(emulatorConfiguration, assets)

            val loadResult = MelonEmulator.bootFirmware()
            if (loadResult != MelonEmulator.FirmwareLoadResult.SUCCESS)
                throw FirmwareLoadFailedException(loadResult)

            emitter.onComplete()
        }
    }

    override fun getEmulatorConfiguration(): EmulatorConfiguration {
        return viewModel.getEmulatorConfigurationForFirmware(firmwareConsoleType)
    }

    override fun getPauseMenuOptions(): List<PauseMenuOption> {
        return FirmwarePauseMenuOptions.values().toList()
    }

    override fun onPauseMenuOptionSelected(option: PauseMenuOption) {
        when (option) {
            FirmwarePauseMenuOptions.SETTINGS -> openSettings()
            FirmwarePauseMenuOptions.EXIT -> finish()
        }
    }
}