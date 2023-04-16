package me.magnum.melonds.ui.emulator.firmware

import android.os.Bundle
import android.widget.Toast
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.ui.emulator.EmulatorActivity
import me.magnum.melonds.ui.emulator.EmulatorDelegate
import me.magnum.melonds.ui.emulator.PauseMenuOption

class FirmwareEmulatorDelegate(activity: EmulatorActivity) : EmulatorDelegate(activity) {

    private lateinit var firmwareConsoleType: ConsoleType

    override fun getEmulatorSetupObservable(extras: Bundle?) {
        val consoleTypeParameter = extras?.getInt(EmulatorActivity.KEY_BOOT_FIRMWARE_CONSOLE, -1)
        if (consoleTypeParameter == null || consoleTypeParameter == -1) {
            throw RuntimeException("No console type specified")
        }

        firmwareConsoleType = ConsoleType.values()[consoleTypeParameter]
        activity.viewModel.loadFirmware(firmwareConsoleType)
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

    override fun dispose() {
    }
}