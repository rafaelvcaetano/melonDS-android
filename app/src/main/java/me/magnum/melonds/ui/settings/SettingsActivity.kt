package me.magnum.melonds.ui.settings

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.*
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.preferences.DirectoryPickerPreference
import me.magnum.melonds.utils.ConfigurationUtils
import me.magnum.melonds.utils.DirectoryPickerContract
import me.magnum.melonds.utils.FileUtils

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {
    companion object {
        private val sBindPreferenceSummaryToValueListener = Preference.OnPreferenceChangeListener { preference, value ->
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
                is DirectoryPickerPreference -> {
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

        private fun bindPreferenceSummaryToValue(preference: Preference?) {
            if (preference == null)
                return

            // Set the listener to watch for value changes.
            preference.onPreferenceChangeListener = sBindPreferenceSummaryToValueListener

            // Trigger the listener immediately with the preference's
            // current value. Special handling for directory pickers since sets can't be converted
            // to string
            if (preference is DirectoryPickerPreference) {
                sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                        PreferenceManager
                                .getDefaultSharedPreferences(preference.context)
                                .getStringSet(preference.key, null))
            } else {
                sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                        PreferenceManager
                                .getDefaultSharedPreferences(preference.context)
                                .getString(preference.key, null))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupActionBar()
        supportFragmentManager
                .beginTransaction()
                .replace(android.R.id.content, MainPreferencesFragment())
                .commit()
    }

    private fun setupActionBar() {
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == android.R.id.home) {
            if (!super.onOptionsItemSelected(item)) {
                finish()
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    class MainPreferencesFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.pref_main, rootKey)
            val romDirsPreference = findPreference<DirectoryPickerPreference>("rom_search_dirs")!!
            val consoleTypePreference = findPreference<ListPreference>("console_type")!!
            val dsBiosDirPreference = findPreference<DirectoryPickerPreference>("bios_dir")!!
            val dsiBiosDirPreference = findPreference<DirectoryPickerPreference>("dsi_bios_dir")!!
            val sramDirPreference = findPreference<DirectoryPickerPreference>("sram_dir")!!
            val jitPreference = findPreference<SwitchPreference>("enable_jit")!!
            bindPreferenceSummaryToValue(romDirsPreference)
            bindPreferenceSummaryToValue(dsBiosDirPreference)
            bindPreferenceSummaryToValue(dsiBiosDirPreference)
            bindPreferenceSummaryToValue(sramDirPreference)

            val romPickerLauncher = registerForActivityResult(DirectoryPickerContract(), romDirsPreference::onDirectoryPicked)
            val dsBiosPickerLauncher = registerForActivityResult(DirectoryPickerContract(), dsBiosDirPreference::onDirectoryPicked)
            val dsiBiosPickerLauncher = registerForActivityResult(DirectoryPickerContract(), dsiBiosDirPreference::onDirectoryPicked)
            val sramPickerLauncher = registerForActivityResult(DirectoryPickerContract(), sramDirPreference::onDirectoryPicked)

            if (Build.SUPPORTED_64_BIT_ABIS.isEmpty()) {
                jitPreference.isEnabled = false
                jitPreference.isChecked = false
                jitPreference.setSummary(R.string.jit_not_supported)
            }

            consoleTypePreference.setOnPreferenceChangeListener { _, newValue ->
                val consoleTypePreferenceValue = newValue as String
                val newConsoleType = ConsoleType.valueOfIgnoreCase(consoleTypePreferenceValue)
                val newTypeBiosDir = when(newConsoleType) {
                    ConsoleType.DS -> dsBiosDirPreference.getPersistedStringSet(null)?.firstOrNull()?.let { Uri.parse(it) }
                    ConsoleType.DSi -> dsiBiosDirPreference.getPersistedStringSet(null)?.firstOrNull()?.let { Uri.parse(it) }
                }

                if (ConfigurationUtils.checkConfigurationDirectory(requireContext(), newTypeBiosDir, newConsoleType) != ConfigurationUtils.ConfigurationDirStatus.VALID) {
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
            romDirsPreference.setOnPreferenceClickListener { preference ->
                romPickerLauncher.launch(preference.getPersistedStringSet(null)?.firstOrNull()?.let { Uri.parse(it) })
                true
            }
            dsBiosDirPreference.setOnPreferenceClickListener { preference ->
                dsBiosPickerLauncher.launch(preference.getPersistedStringSet(null)?.firstOrNull()?.let { Uri.parse(it) })
                true
            }
            dsiBiosDirPreference.setOnPreferenceClickListener { preference ->
                dsiBiosPickerLauncher.launch(preference.getPersistedStringSet(null)?.firstOrNull()?.let { Uri.parse(it) })
                true
            }
            sramDirPreference.setOnPreferenceClickListener { preference ->
                sramPickerLauncher.launch(preference.getPersistedStringSet(null)?.firstOrNull()?.let { Uri.parse(it) })
                true
            }
        }
    }
}