package me.magnum.melonds.ui

import android.os.Bundle
import android.text.TextUtils
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.github.angads25.filepicker.view.FilePickerPreference
import me.magnum.melonds.R
import me.magnum.melonds.utils.PreferenceDirectoryUtils.getMultipleDirectoryFromPreference

class SettingsActivity : AppCompatActivity() {
    companion object {
        private val sBindPreferenceSummaryToValueListener = Preference.OnPreferenceChangeListener { preference, value ->
            val stringValue = value.toString()
            when (preference) {
                is FilePickerPreference -> {
                    var directory = getMultipleDirectoryFromPreference(stringValue)
                    if (directory.isEmpty())
                        directory = arrayOf(preference.getContext().getString(R.string.not_set))

                    preference.setSummary(TextUtils.join("\n", directory))
                }
                is ListPreference -> {
                    // For list preferences, look up the correct display value in
                    // the preference's 'entries' list.
                    val index = preference.findIndexOfValue(stringValue)

                    // Set the summary to reflect the new value.
                    val summary = if (index >= 0)
                        preference.entries[index]
                    else
                        preference.getContext().getString(R.string.not_set)
                    preference.setSummary(summary)
                }
                else -> {
                    // For all other preferences, set the summary to the value's
                    // simple string representation.
                    preference.summary = stringValue
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
            // current value.
            sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                    PreferenceManager
                            .getDefaultSharedPreferences(preference.context)
                            .getString(preference.key, ""))
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
            bindPreferenceSummaryToValue(findPreference("rom_search_dirs"))
            bindPreferenceSummaryToValue(findPreference("bios_dir"))
            bindPreferenceSummaryToValue(findPreference("sram_dir"))
        }
    }
}