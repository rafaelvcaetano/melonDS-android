package me.magnum.melonds.ui.settings.fragments

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.magnum.melonds.R
import me.magnum.melonds.common.BiosGuidedFolderSetup
import me.magnum.melonds.common.DirectoryAccessValidator
import me.magnum.melonds.common.Permission
import me.magnum.melonds.common.UriPermissionManager
import me.magnum.melonds.common.contracts.DirectoryPickerContract
import me.magnum.melonds.domain.model.ConfigurationDirResult
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.ui.settings.viewmodel.CustomFirmwareViewModel
import me.magnum.melonds.ui.settings.PreferenceFragmentHelper
import me.magnum.melonds.ui.settings.PreferenceFragmentTitleProvider
import me.magnum.melonds.ui.settings.preferences.BiosDirectoryPickerPreference
import me.magnum.melonds.utils.enumValueOfIgnoreCase
import javax.inject.Inject
import java.io.IOException

@AndroidEntryPoint
class CustomFirmwarePreferencesFragment : BasePreferenceFragment(), PreferenceFragmentTitleProvider {

    private val viewModel by viewModels<CustomFirmwareViewModel>()
    private var guidedSetupProgressDialog: AlertDialog? = null
    private val helper by lazy { PreferenceFragmentHelper(this, uriPermissionManager, directoryAccessValidator) }
    @Inject lateinit var uriPermissionManager: UriPermissionManager
    @Inject lateinit var directoryAccessValidator: DirectoryAccessValidator

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_custom_firmware, rootKey)
        val consoleTypePreference = findPreference<ListPreference>("console_type")!!
        val dsBiosDirPreference = findPreference<BiosDirectoryPickerPreference>("bios_dir")!!
        val dsiBiosDirPreference = findPreference<BiosDirectoryPickerPreference>("dsi_bios_dir")!!

        helper.setupStoragePickerPreference(dsBiosDirPreference)
        helper.setupStoragePickerPreference(dsiBiosDirPreference)

        val biosValidator = object : BiosDirectoryPickerPreference.BiosDirectoryValidator {
            override fun getBiosDirectoryValidationResult(consoleType: ConsoleType, directory: Uri?): ConfigurationDirResult {
                return viewModel.getConsoleConfigurationDirectoryStatus(consoleType, directory)
            }
        }

        dsBiosDirPreference.setBiosDirectoryValidator(biosValidator)
        dsiBiosDirPreference.setBiosDirectoryValidator(biosValidator)

        val guidedSetupLauncher = registerForActivityResult(DirectoryPickerContract(Permission.READ_WRITE)) { uri ->
            if (uri != null) {
                runGuidedBiosSetup(uri, dsBiosDirPreference, dsiBiosDirPreference)
            }
        }
        findPreference<Preference>("guided_bios_setup")?.setOnPreferenceClickListener {
            // Explain what is needed before opening the picker, not after it fails. Two folders
            // are unavoidable: DS and DSi use the same three file names for different files, so
            // they cannot share one folder. The one case content cannot resolve is called out
            // explicitly -- the two DSi BIOS images are both exactly 64 KB with nothing in them
            // to tell them apart, so only their names can.
            AlertDialog.Builder(requireContext())
                    .setTitle(R.string.bios_guided_setup_title)
                    .setMessage(R.string.bios_guided_setup_explanation)
                    .setPositiveButton(R.string.bios_guided_setup_choose_folder) { _, _ ->
                        guidedSetupLauncher.launch(null)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            true
        }

        consoleTypePreference.setOnPreferenceChangeListener { _, newValue ->
            val consoleTypePreferenceValue = newValue as String
            val newConsoleType = enumValueOfIgnoreCase<ConsoleType>(consoleTypePreferenceValue)

            val validationResult = viewModel.getConsoleConfigurationDirectoryStatus(newConsoleType)
            if (validationResult.status != ConfigurationDirResult.Status.VALID) {
                AlertDialog.Builder(requireContext())
                        .setMessage(getIncorrectBiosDirMessage(validationResult))
                        .setPositiveButton(R.string.ok, null)
                        .show()
            }

            true
        }
    }

    override fun getTitle() = getString(R.string.custom_bios_firmware)

    override fun onDestroyView() {
        guidedSetupProgressDialog?.dismiss()
        guidedSetupProgressDialog = null
        super.onDestroyView()
    }

    /**
     * Used to show the same generic "not correct" text for three different problems: a missing
     * file, a file with the wrong size, and a folder that actually holds the other console's
     * BIOS/firmware. Now picked per aggregate state: WRONG_CONSOLE takes priority (it names the
     * likely actual cause via the existing ds/dsi_incorrect_bios_dir_info text), then MISSING
     * files are named explicitly since they're the most actionable problem, then INVALID (wrong
     * size) files.
     */
    private fun getIncorrectBiosDirMessage(validationResult: ConfigurationDirResult): String {
        val wrongConsoleFiles = validationResult.fileResults.filter { it.second == ConfigurationDirResult.FileStatus.WRONG_CONSOLE }.map { it.first }
        val missingFiles = validationResult.fileResults.filter { it.second == ConfigurationDirResult.FileStatus.MISSING }.map { it.first }
        val invalidFiles = validationResult.fileResults.filter { it.second == ConfigurationDirResult.FileStatus.INVALID }.map { it.first }

        return when {
            wrongConsoleFiles.isNotEmpty() -> {
                val textRes = when (validationResult.consoleType) {
                    ConsoleType.DS -> R.string.ds_incorrect_bios_dir_info
                    ConsoleType.DSi -> R.string.dsi_incorrect_bios_dir_info
                }
                getString(textRes)
            }
            missingFiles.isNotEmpty() -> {
                val textRes = when (validationResult.consoleType) {
                    ConsoleType.DS -> R.string.ds_bios_dir_missing_files_info
                    ConsoleType.DSi -> R.string.dsi_bios_dir_missing_files_info
                }
                getString(textRes, missingFiles.joinToString(", "))
            }
            invalidFiles.isNotEmpty() -> {
                val textRes = when (validationResult.consoleType) {
                    ConsoleType.DS -> R.string.ds_bios_dir_invalid_size_files_info
                    ConsoleType.DSi -> R.string.dsi_bios_dir_invalid_size_files_info
                }
                getString(textRes, invalidFiles.joinToString(", "))
            }
            else -> {
                val textRes = when (validationResult.consoleType) {
                    ConsoleType.DS -> R.string.ds_incorrect_bios_dir_info
                    ConsoleType.DSi -> R.string.dsi_incorrect_bios_dir_info
                }
                getString(textRes)
            }
        }
    }

    /**
     * Turns a single folder of loosely-named BIOS/firmware files into the "DS" and "DSi"
     * sub-folders melonDS expects, with the recognised files copied into the right one under
     * the right name, then points the DS/DSi BIOS directory preferences at those sub-folders.
     * See [BiosGuidedFolderSetup] for exactly what is and isn't recognised.
     *
     * The copy itself (which can mean gigabytes, e.g. a DSi nand.bin) runs off the main thread;
     * a progress dialog covers the wait and is dismissed when the view is destroyed. Guided
     * runs are serialized; cancellation attempts to remove the current partial copy once provider
     * IO returns. A provider can still refuse deletion.
     */
    private fun runGuidedBiosSetup(parentDirectoryUri: Uri, dsBiosDirPreference: BiosDirectoryPickerPreference, dsiBiosDirPreference: BiosDirectoryPickerPreference) {
        if (directoryAccessValidator.getDirectoryAccessForPermission(parentDirectoryUri, Permission.READ_WRITE) != DirectoryAccessValidator.DirectoryAccessResult.OK) {
            AlertDialog.Builder(requireContext())
                    .setTitle(R.string.error_invalid_directory)
                    .setMessage(R.string.error_invalid_directory_description)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            return
        }

        val appContext = requireContext().applicationContext
        val progressDialog = AlertDialog.Builder(requireContext())
                .setMessage(R.string.bios_guided_setup_in_progress)
                .setCancelable(false)
                .show()
        guidedSetupProgressDialog = progressDialog

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val report = BiosGuidedFolderSetup(appContext).runCancellable(parentDirectoryUri)
                applyGuidedBiosSetupResult(report, parentDirectoryUri, dsBiosDirPreference, dsiBiosDirPreference)
            } catch (e: IOException) {
                showGuidedSetupFailure(parentDirectoryUri, dsBiosDirPreference, dsiBiosDirPreference)
            } catch (e: SecurityException) {
                showGuidedSetupFailure(parentDirectoryUri, dsBiosDirPreference, dsiBiosDirPreference)
            } catch (e: IllegalArgumentException) {
                showGuidedSetupFailure(parentDirectoryUri, dsBiosDirPreference, dsiBiosDirPreference)
            } finally {
                progressDialog.dismiss()
                if (guidedSetupProgressDialog === progressDialog) guidedSetupProgressDialog = null
            }
        }
    }

    private fun applyGuidedBiosSetupResult(
        report: BiosGuidedFolderSetup.Report?,
        parentDirectoryUri: Uri,
        dsBiosDirPreference: BiosDirectoryPickerPreference,
        dsiBiosDirPreference: BiosDirectoryPickerPreference,
    ) {
        if (report == null) {
            showGuidedSetupFailure(parentDirectoryUri, dsBiosDirPreference, dsiBiosDirPreference)
            return
        }

        // One permission grant on the picked folder covers the DS/DSi sub-folders created or
        // found inside it; each is then persisted as its own console's BIOS directory.
        uriPermissionManager.persistDirectoryPermissions(parentDirectoryUri, Permission.READ_WRITE)

        // Only re-point a preference when the console it belongs to actually ended up complete:
        // an existing, valid directory shouldn't be swapped for one that's still missing files.
        val configuredConsoles = mutableSetOf<ConsoleType>()
        if (allMandatorySlotsSatisfied(report, ConsoleType.DS)) {
            report.dsDirectory?.let {
                if (isValidDirectory(ConsoleType.DS, it.uri)) {
                    pointPreferenceAt(dsBiosDirPreference, it.uri)
                    configuredConsoles += ConsoleType.DS
                }
            }
        }
        if (allMandatorySlotsSatisfied(report, ConsoleType.DSi)) {
            report.dsiDirectory?.let {
                // DS has already been applied if complete; DSi validation checks that prerequisite.
                if (isValidDirectory(ConsoleType.DSi, it.uri)) {
                    pointPreferenceAt(dsiBiosDirPreference, it.uri)
                    configuredConsoles += ConsoleType.DSi
                }
            }
        }

        // Refresh both displayed statuses, including a directory retained from earlier setup.
        dsBiosDirPreference.revalidate()
        dsiBiosDirPreference.revalidate()

        AlertDialog.Builder(requireContext())
                .setTitle(R.string.bios_guided_setup_result_title)
                .setMessage(buildGuidedSetupSummary(report, configuredConsoles))
                .setPositiveButton(R.string.ok, null)
                .apply {
                    if (report.unreadableSourceNames.isNotEmpty() || report.slotResults.any { it.outcome.isFailure() } || report.nandResult.outcome.isFailure()) {
                        setNeutralButton(R.string.retry) { _, _ -> runGuidedBiosSetup(parentDirectoryUri, dsBiosDirPreference, dsiBiosDirPreference) }
                    }
                }
                .show()
    }

    private fun showGuidedSetupFailure(
        parentDirectoryUri: Uri,
        dsPreference: BiosDirectoryPickerPreference,
        dsiPreference: BiosDirectoryPickerPreference,
    ) {
        AlertDialog.Builder(requireContext())
                .setTitle(R.string.bios_guided_setup_result_title)
                .setMessage(R.string.bios_guided_setup_failed)
                .setPositiveButton(R.string.retry) { _, _ -> runGuidedBiosSetup(parentDirectoryUri, dsPreference, dsiPreference) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
    }

    private fun isValidDirectory(consoleType: ConsoleType, directory: Uri): Boolean {
        return try {
            viewModel.getConsoleConfigurationDirectoryStatus(consoleType, directory).status == ConfigurationDirResult.Status.VALID
        } catch (e: IOException) {
            false
        } catch (e: SecurityException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /** Requires all three BIOS/firmware slots and, for DSi, a readable non-empty NAND. */
    private fun allMandatorySlotsSatisfied(report: BiosGuidedFolderSetup.Report, consoleType: ConsoleType): Boolean {
        val outcomesForConsole = report.slotResults.filter { it.slot.consoleType == consoleType }
        return outcomesForConsole.size == 3 && outcomesForConsole.all { it.outcome.isSatisfied() } &&
                (consoleType != ConsoleType.DSi || report.nandResult.outcome.isSatisfied())
    }

    private fun BiosGuidedFolderSetup.Outcome.isSatisfied() =
        this == BiosGuidedFolderSetup.Outcome.CREATED || this == BiosGuidedFolderSetup.Outcome.ALREADY_PRESENT

    private fun BiosGuidedFolderSetup.Outcome.isFailure() =
        this == BiosGuidedFolderSetup.Outcome.ERROR || this == BiosGuidedFolderSetup.Outcome.CLEANUP_FAILED

    /**
     * Points a BIOS directory preference at a sub-folder created inside an already-persisted
     * parent tree. The preference also tries to persist a permission of its own for this exact
     * URI, which is redundant (the parent grant already covers it, see [runGuidedBiosSetup]) but
     * harmless if the platform rejects a persistable request for a URI that was never itself the
     * direct subject of a document-picker grant; either way access to the sub-folder is retained.
     */
    private fun pointPreferenceAt(preference: BiosDirectoryPickerPreference, directoryUri: Uri) {
        try {
            preference.onDirectoryPicked(directoryUri)
        } catch (e: SecurityException) {
            // Ignored: see the doc comment above.
        }
    }

    private fun buildGuidedSetupSummary(report: BiosGuidedFolderSetup.Report, configuredConsoles: Set<ConsoleType>): String {
        val sourceErrors = if (report.unreadableSourceNames.isEmpty()) "" else {
            getString(R.string.bios_guided_setup_unreadable_sources, report.unreadableSourceNames.joinToString(", ")) + "\n\n"
        }
        return sourceErrors + listOf(ConsoleType.DS, ConsoleType.DSi).joinToString("\n\n") { console ->
            val consoleLabel = getString(if (console == ConsoleType.DS) R.string.console_ds else R.string.console_dsi)
            val status = getString(if (console in configuredConsoles) R.string.bios_guided_setup_directory_selected else R.string.bios_guided_setup_directory_unchanged)
            val lines = report.slotResults.filter { it.slot.consoleType == console }.map { result ->
                guidedSetupOutcomeText(consoleLabel, result.slot.canonicalFileName, result.outcome, result.sourceFileNames, result.resolvedByFileNameHint, result.cleanupFileName)
            }.toMutableList()
            if (console == ConsoleType.DSi) {
                lines += guidedSetupOutcomeText(consoleLabel, BiosGuidedFolderSetup.NAND_FILE_NAME, report.nandResult.outcome, listOfNotNull(report.nandResult.sourceFileName), cleanupFileName = report.nandResult.cleanupFileName)
            }
            "$consoleLabel — $status\n" + lines.joinToString("\n") { "• $it" }
        }
    }

    private fun guidedSetupOutcomeText(
        consoleLabel: String,
        fileName: String,
        outcome: BiosGuidedFolderSetup.Outcome,
        sourceNames: List<String>,
        byNameHint: Boolean = false,
        cleanupFileName: String? = null,
    ): String = when (outcome) {
        BiosGuidedFolderSetup.Outcome.CREATED -> getString(
            if (byNameHint) R.string.bios_guided_setup_created_by_name_hint else R.string.bios_guided_setup_created,
            consoleLabel, fileName, sourceNames.firstOrNull().orEmpty(),
        )
        BiosGuidedFolderSetup.Outcome.ALREADY_PRESENT -> getString(R.string.bios_guided_setup_already_present, consoleLabel, fileName)
        BiosGuidedFolderSetup.Outcome.MISSING -> getString(R.string.bios_guided_setup_missing, consoleLabel, fileName)
        BiosGuidedFolderSetup.Outcome.AMBIGUOUS -> getString(R.string.bios_guided_setup_ambiguous, consoleLabel, fileName, sourceNames.joinToString(", "))
        BiosGuidedFolderSetup.Outcome.INVALID -> getString(R.string.bios_guided_setup_invalid, consoleLabel, fileName)
        BiosGuidedFolderSetup.Outcome.ERROR -> getString(R.string.bios_guided_setup_copy_error, consoleLabel, fileName)
        BiosGuidedFolderSetup.Outcome.CLEANUP_FAILED -> getString(R.string.bios_guided_setup_cleanup_failed, consoleLabel, fileName, cleanupFileName.orEmpty())
    }
}
