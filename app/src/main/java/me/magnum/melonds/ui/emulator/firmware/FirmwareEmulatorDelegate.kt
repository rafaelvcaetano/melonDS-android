package me.magnum.melonds.ui.emulator.firmware

import android.os.Bundle
import io.reactivex.Completable
import io.reactivex.Single
import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.EmulatorConfiguration
import me.magnum.melonds.ui.emulator.EmulatorActivity
import me.magnum.melonds.ui.emulator.EmulatorDelegate

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
        return getEmulatorLaunchConfiguration(firmwareConsoleType).flatMapCompletable { emulatorConfiguration ->
            Completable.create {
                activity.viewModel.loadLayoutForFirmware()
                MelonEmulator.setupEmulator(emulatorConfiguration, activity.assets, activity.buildUriFileHandler(), activity.getRendererTextureBuffer())

                val loadResult = MelonEmulator.bootFirmware()
                if (loadResult != MelonEmulator.FirmwareLoadResult.SUCCESS) {
                    it.onError(EmulatorActivity.FirmwareLoadFailedException(loadResult))
                } else {
                    it.onComplete()
                }
            }
        }
    }

    private fun getEmulatorLaunchConfiguration(consoleType: ConsoleType): Single<EmulatorConfiguration> {
        val baseEmulatorConfiguration = activity.viewModel.getEmulatorConfigurationForFirmware(consoleType)
        return activity.adjustEmulatorConfigurationForPermissions(baseEmulatorConfiguration, true)
    }

    override fun getEmulatorConfiguration(): EmulatorConfiguration {
        val baseEmulatorConfiguration = activity.viewModel.getEmulatorConfigurationForFirmware(firmwareConsoleType)
        return activity.adjustEmulatorConfigurationForPermissions(baseEmulatorConfiguration, false).blockingGet()
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