package me.magnum.melonds.ui.emulator.firmware

import android.os.Bundle
import android.widget.Toast
import io.reactivex.Completable
import io.reactivex.Single
import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.EmulatorConfiguration
import me.magnum.melonds.ui.emulator.EmulatorActivity
import me.magnum.melonds.ui.emulator.EmulatorDelegate
import me.magnum.melonds.ui.emulator.PauseMenuOption

class FirmwareEmulatorDelegate(activity: EmulatorActivity) : EmulatorDelegate(activity) {

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
                MelonEmulator.setupEmulator(emulatorConfiguration, activity.assets, activity.getRendererTextureBuffer())

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

    override fun getPauseMenuOptions(): List<PauseMenuOption> {
        return activity.viewModel.getFirmwarePauseMenuOptions()
    }

    override fun onPauseMenuOptionSelected(option: PauseMenuOption) {
        when (option) {
            FirmwarePauseMenuOption.SETTINGS -> activity.openSettings()
            FirmwarePauseMenuOption.RESET -> activity.resetEmulation()
            FirmwarePauseMenuOption.EXIT -> activity.finish()
        }
    }

    override fun performQuickSave() {
        showSaveStatesNotSupportedToast()
    }

    override fun performQuickLoad() {
        showSaveStatesNotSupportedToast()
    }

    private fun showSaveStatesNotSupportedToast() {
        Toast.makeText(activity, R.string.save_states_not_supported, Toast.LENGTH_LONG).show()
    }

    override fun getCrashContext(): Any {
        return FirmwareCrashContext(getEmulatorConfiguration(), activity.viewModel.getDsBiosDirectory()?.toString(), activity.viewModel.getDsiBiosDirectory()?.toString())
    }

    override fun dispose() {
    }

    private data class FirmwareCrashContext(val emulatorConfiguration: EmulatorConfiguration, val dsiBiosDirUri: String?, val dsBiosDirUri: String?)
}