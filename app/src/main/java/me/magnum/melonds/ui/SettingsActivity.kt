package me.magnum.melonds.ui

import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.github.angads25.filepicker.view.FilePickerPreference
import me.magnum.melonds.R
import me.magnum.melonds.preferences.DirectoryPickerPreference
import me.magnum.melonds.utils.DirectoryPickerContract
import me.magnum.melonds.utils.FileUtils
import me.magnum.melonds.utils.PreferenceDirectoryUtils.getMultipleDirectoryFromPreference

class SettingsActivity : AppCompatActivity() {
    companion object {
        private val sBindPreferenceSummaryToValueListener = Preference.OnPreferenceChangeListener { preference, value ->
            when (preference) {
                is FilePickerPreference -> {
                    var directory = getMultipleDirectoryFromPreference(value.toString())
                    if (directory.isEmpty())
                        directory = arrayOf(preference.getContext().getString(R.string.not_set))

                    preference.setSummary(directory.joinToString("\n"))
                }
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
            val biosDirPreference = findPreference<DirectoryPickerPreference>("bios_dir")!!
            val sramDirPreference = findPreference<DirectoryPickerPreference>("sram_dir")!!
            bindPreferenceSummaryToValue(romDirsPreference)
            bindPreferenceSummaryToValue(biosDirPreference)
            bindPreferenceSummaryToValue(sramDirPreference)

            val romPickerLauncher = registerForActivityResult(DirectoryPickerContract(), romDirsPreference::onDirectoryPicked)
            val biosPickerLauncher = registerForActivityResult(DirectoryPickerContract(), biosDirPreference::onDirectoryPicked)
            val sramPickerLauncher = registerForActivityResult(DirectoryPickerContract(), sramDirPreference::onDirectoryPicked)

            romDirsPreference.setOnPreferenceClickListener {
                romPickerLauncher.launch(null)
                true
            }
            biosDirPreference.setOnPreferenceClickListener {
                biosPickerLauncher.launch(null)
                true
            }
            sramDirPreference.setOnPreferenceClickListener {
                sramPickerLauncher.launch(null)
                true
            }
        }
    }
}