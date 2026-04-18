package me.magnum.melonds.ui.common.rom

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.ConfigurationDirResult
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.emulator.validation.FirmwareLaunchPreconditionCheckResult
import me.magnum.melonds.domain.model.emulator.validation.RomLaunchPreconditionCheckResult
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.ui.common.rom.model.LaunchValidationResult
import me.magnum.melonds.ui.dsiwaremanager.DSiWareManagerActivity
import me.magnum.melonds.ui.settings.SettingsActivity

class EmulatorLaunchValidatorDelegate(
    private val context: ComponentActivity,
    private val callback: Callback,
) {

    private val viewModel by context.viewModels<EmulatorLaunchValidationViewModel>()

    private val firmwareSettingsLauncher = context.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onReturnFromFirmwareSettings()
    }
    private val dsiWareManagerLauncher = context.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onReturnFromDsiWareManagerSetup()
    }

    init {
        context.lifecycleScope.launch {
            context.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.romValidationResult.collect {
                    when (it) {
                        is LaunchValidationResult.Firmware -> {
                            when (it.result) {
                                is FirmwareLaunchPreconditionCheckResult.Success -> callback.onFirmwareValidated(it.result.consoleType)
                                is FirmwareLaunchPreconditionCheckResult.BiosConfigurationIncorrect -> {
                                    showIncorrectConfigurationDirectoryDialogForFirmware(it.result.configurationDirectoryResult)
                                }
                            }
                        }
                        is LaunchValidationResult.Rom -> {
                            when (it.result) {
                                is RomLaunchPreconditionCheckResult.Success -> callback.onRomValidated(it.result.rom)
                                is RomLaunchPreconditionCheckResult.DSiWareTitleValidationFailed -> showDsiWareTitleLoadIssueDialog(it.result.reason)
                                is RomLaunchPreconditionCheckResult.BiosConfigurationIncorrect -> {
                                    showIncorrectConfigurationDirectoryDialogForRom(it.result.configurationDirectoryResult)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun validateRom(rom: Rom) {
        viewModel.validateRomForLaunch(rom)
    }

    fun validateFirmware(consoleType: ConsoleType) {
        viewModel.validateFirmwareForLaunch(consoleType)
    }

    private fun showIncorrectConfigurationDirectoryDialogForFirmware(configurationDirResult: ConfigurationDirResult) {
        AlertDialog.Builder(context)
            .setTitle(R.string.firmware_launch_failed)
            .setMessage(R.string.firmware_launch_bad_setup)
            .setPositiveButton(R.string.settings) { _, _ ->
                val intent = Intent(context, SettingsActivity::class.java).apply {
                    putExtra(SettingsActivity.KEY_ENTRY_POINT, SettingsActivity.CUSTOM_FIRMWARE_ENTRY_POINT)
                }
                firmwareSettingsLauncher.launch(intent)
            }
            .setNegativeButton(R.string.cancel) { _, _ -> callback.onValidationAborted() }
            .setOnCancelListener { callback.onValidationAborted() }
            .show()
    }

    private fun showIncorrectConfigurationDirectoryDialogForRom(configurationDirResult: ConfigurationDirResult) {
        AlertDialog.Builder(context)
            .setTitle(R.string.rom_launch_failed)
            .setMessage(R.string.rom_launch_custom_bios_firmware_bad_setup)
            .setPositiveButton(R.string.settings) { _, _ ->
                val intent = Intent(context, SettingsActivity::class.java).apply {
                    putExtra(SettingsActivity.KEY_ENTRY_POINT, SettingsActivity.CUSTOM_FIRMWARE_ENTRY_POINT)
                }
                firmwareSettingsLauncher.launch(intent)
            }
            .setNegativeButton(R.string.cancel) { _, _ -> callback.onValidationAborted() }
            .setOnCancelListener { callback.onValidationAborted() }
            .show()
    }

    private fun showDsiWareTitleLoadIssueDialog(reason: RomLaunchPreconditionCheckResult.DSiWareTitleValidationFailed.Reason) {
        val message = when (reason) {
            RomLaunchPreconditionCheckResult.DSiWareTitleValidationFailed.Reason.NandError -> context.getString(R.string.failed_launch_dsiware_title_check_failed)
            RomLaunchPreconditionCheckResult.DSiWareTitleValidationFailed.Reason.RomParseError -> context.getString(R.string.failed_launch_dsiware_title_rom_failed)
            RomLaunchPreconditionCheckResult.DSiWareTitleValidationFailed.Reason.TitleNotInstalled -> context.getString(R.string.failed_launch_dsiware_title_not_installed)
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.failed_launch_dsiware_title)
            .setMessage(message)
            .setPositiveButton(R.string.dsiware_manager) { _, _ ->
                val intent = Intent(context, DSiWareManagerActivity::class.java)
                dsiWareManagerLauncher.launch(intent)
            }
            .setNegativeButton(R.string.cancel) { _, _ -> callback.onValidationAborted() }
            .setOnCancelListener {
                callback.onValidationAborted()
            }
            .show()
    }

    private fun showInvalidDirectoryAccessDialog() {
        AlertDialog.Builder(context)
            .setTitle(R.string.error_invalid_directory)
            .setMessage(R.string.error_invalid_directory_description)
            .setPositiveButton(R.string.ok, null)
            .setOnDismissListener { callback.onValidationAborted() }
            .show()
    }

    interface Callback {
        fun onRomValidated(rom: Rom)
        fun onFirmwareValidated(consoleType: ConsoleType)
        fun onValidationAborted()
    }
}