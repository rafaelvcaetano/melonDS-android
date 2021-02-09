package me.magnum.melonds.ui.settings

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.preference.*
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.MicSource
import me.magnum.melonds.ui.settings.preferences.StoragePickerPreference
import me.magnum.melonds.utils.*
import java.util.*

@AndroidEntryPoint
class MainPreferencesFragment : PreferenceFragmentCompat() {
    private companion object {
        val sBindPreferenceSummaryToValueListener = Preference.OnPreferenceChangeListener { preference, value ->
            when (preference) {
                is ListPreference -> {
                    // For list preferences, look up the correct display value in
                    // the preference's 'entries' list.
                    val index = preference.findIndexOfValue(value.toString())

                    // Set the summary to reflect the new value.
                    val summary = if (index >= 0)
                        preference.entries[index]
                    else
                        preference.getContext().getString(R.string.not_set)
                    preference.setSummary(summary)
                }
                is StoragePickerPreference -> {
                    if (value == null || value !is Set<*> || value.isEmpty())
                        preference.summary = preference.getContext().getString(R.string.not_set)
                    else {
                        val uris = value.mapNotNull { FileUtils.getAbsolutePathFromSAFUri(preference.context, Uri.parse(it as String)) }
                        preference.summary = uris.joinToString("\n")
                    }
                }
                else -> {
                    // For all other preferences, set the summary to the value's
                    // simple string representation.
                    preference.summary = value.toString()
                }
            }
            true
        }

        fun bindPreferenceSummaryToValue(preference: Preference?) {
            if (preference == null)
                return

            // Set the listener to watch for value changes.
            preference.onPreferenceChangeListener = sBindPreferenceSummaryToValueListener

            // Trigger the listener immediately with the preference's
            // current value. Special handling for directory pickers since sets can't be converted
            // to string
            if (preference is StoragePickerPreference) {
                sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                        PreferenceManager.getDefaultSharedPreferences(preference.context)
                                .getStringSet(preference.key, null))
            } else {
                sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                        PreferenceManager.getDefaultSharedPreferences(preference.context)
                                .getString(preference.key, null))
            }
        }
    }

    private val viewModel: SettingsViewModel by activityViewModels()

    private lateinit var micSourcePreference: ListPreference
    private val microphonePermissionLauncher by lazy {
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                micSourcePreference.value = MicSource.DEVICE.name.toLowerCase(Locale.ROOT)
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_main, rootKey)
        val consoleTypePreference = findPreference<ListPreference>("console_type")!!
        val dsBiosDirPreference = findPreference<StoragePickerPreference>("bios_dir")!!
        val dsiBiosDirPreference = findPreference<StoragePickerPreference>("dsi_bios_dir")!!
        val jitPreference = findPreference<SwitchPreference>("enable_jit")!!
        val importCheatsPreference = findPreference<Preference>("cheats_import")!!
        micSourcePreference = findPreference("mic_source")!!

        setupStoragePickerPreference(dsBiosDirPreference)
        setupStoragePickerPreference(dsiBiosDirPreference)
        setupStoragePickerPreference(findPreference("rom_search_dirs")!!)
        setupStoragePickerPreference(findPreference("sram_dir")!!)

        if (Build.SUPPORTED_64_BIT_ABIS.isEmpty()) {
            jitPreference.isEnabled = false
            jitPreference.isChecked = false
            jitPreference.setSummary(R.string.jit_not_supported)
        }

        val currentMicSource = enumValueOfIgnoreCase<MicSource>(micSourcePreference.value as String)
        if (currentMicSource == MicSource.DEVICE && !isMicrophonePermissionGranted(requireContext())) {
            micSourcePreference.value = MicSource.BLOW.name.toLowerCase(Locale.ROOT)
        }

        consoleTypePreference.setOnPreferenceChangeListener { _, newValue ->
            val consoleTypePreferenceValue = newValue as String
            val newConsoleType = enumValueOfIgnoreCase<ConsoleType>(consoleTypePreferenceValue)
            val newTypeBiosDir = when(newConsoleType) {
                ConsoleType.DS -> dsBiosDirPreference.getPersistedStringSet(null)?.firstOrNull()?.let { Uri.parse(it) }
                ConsoleType.DSi -> dsiBiosDirPreference.getPersistedStringSet(null)?.firstOrNull()?.let { Uri.parse(it) }
            }

            if (ConfigurationUtils.checkConfigurationDirectory(requireContext(), newTypeBiosDir, newConsoleType).status != ConfigurationUtils.ConfigurationDirStatus.VALID) {
                val textRes = when(newConsoleType) {
                    ConsoleType.DS -> R.string.ds_incorrect_bios_dir_info
                    ConsoleType.DSi -> R.string.dsi_incorrect_bios_dir_info
                }

                AlertDialog.Builder(requireContext())
                        .setMessage(textRes)
                        .setPositiveButton(R.string.ok, null)
                        .show()
            }

            true
        }
        micSourcePreference.setOnPreferenceChangeListener { _, newValue ->
            val newMicSource = enumValueOfIgnoreCase<MicSource>(newValue as String)
            if (newMicSource == MicSource.DEVICE && !isMicrophonePermissionGranted(requireContext())) {
                requestMicrophonePermission(false)
                false
            } else {
                true
            }
        }
        importCheatsPreference.setOnPreferenceClickListener {
            handleCheatsImport()
            true
        }
    }

    private fun setupStoragePickerPreference(storagePreference: StoragePickerPreference) {
        if (storagePreference.selectionType == StoragePickerPreference.SelectionType.FILE) {
            setupFilePickerPreference(storagePreference)
        } else {
            setupDirectoryPickerPreference(storagePreference)
        }
    }

    private fun setupDirectoryPickerPreference(storagePreference: StoragePickerPreference) {
        bindPreferenceSummaryToValue(storagePreference)
        val filePickerLauncher = registerForActivityResult(DirectoryPickerContract(), storagePreference::onDirectoryPicked)
        storagePreference.setOnPreferenceClickListener { preference ->
            val initialUri = preference.getPersistedStringSet(null)?.firstOrNull()?.let { Uri.parse(it) }
            filePickerLauncher.launch(initialUri)
            true
        }
    }

    private fun setupFilePickerPreference(storagePreference: StoragePickerPreference) {
        bindPreferenceSummaryToValue(storagePreference)
        val filePickerLauncher = registerForActivityResult(FilePickerContract(), storagePreference::onDirectoryPicked)
        storagePreference.setOnPreferenceClickListener { preference ->
            val initialUri = preference.getPersistedStringSet(null)?.firstOrNull()?.let { Uri.parse(it) }
            filePickerLauncher.launch(Pair(initialUri, storagePreference.mimeType))
            true
        }
    }

    private fun requestMicrophonePermission(overrideRationaleRequest: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return

        if (!overrideRationaleRequest && shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            AlertDialog.Builder(requireContext())
                    .setTitle(R.string.microphone_permission_required)
                    .setMessage(R.string.microphone_permission_required_info)
                    .setPositiveButton(R.string.ok) { _, _ -> requestMicrophonePermission(true) }
                    .show()
        } else {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun handleCheatsImport() {
        if (viewModel.areCheatsBeingImported()) {
            CheatsImportProgressDialog().show(childFragmentManager, null)
        } else {
            val filePickerLauncher = registerForActivityResult(FilePickerContract(false)) {
                if (it != null) {
                    viewModel.importCheatsDatabase(it)
                    CheatsImportProgressDialog().show(childFragmentManager, null)
                }
            }
            filePickerLauncher.launch(Pair(null, "text/xml"))
        }
    }
}