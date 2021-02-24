package me.magnum.melonds.ui.settings

import android.net.Uri
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.smp.masterswitchpreference.MasterSwitchPreference
import me.magnum.melonds.R
import me.magnum.melonds.ui.settings.preferences.FirmwareBirthdayPreference
import me.magnum.melonds.ui.settings.preferences.StoragePickerPreference
import me.magnum.melonds.utils.DirectoryPickerContract
import me.magnum.melonds.utils.FilePickerContract
import me.magnum.melonds.utils.FileUtils

class PreferenceFragmentHelper(private val activity: PreferenceFragmentCompat) {
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
                is StoragePickerPreference -> {
                    if (value == null || value !is Set<*> || value.isEmpty())
                        preference.summary = preference.getContext().getString(R.string.not_set)
                    else {
                        val uris = value.mapNotNull { FileUtils.getAbsolutePathFromSAFUri(preference.context, Uri.parse(it as String)) }
                        preference.summary = uris.joinToString("\n")
                    }
                }
                is FirmwareBirthdayPreference -> {
                    val birthdayString = (value as String?) ?: "01/01"
                    preference.summary = birthdayString
                }
                is MasterSwitchPreference -> {
                    val isOn = (value as Boolean)
                    preference.summary = if (isOn) preference.context.getString(R.string.on) else preference.context.getString(R.string.off)
                }
                else -> {
                    // For all other preferences, set the summary to the value's
                    // simple string representation.
                    preference.summary = value.toString()
                }
            }
            true
        }
    }

    fun bindPreferenceSummaryToValue(preference: Preference?) {
        if (preference == null)
            return

        // Set the listener to watch for value changes.
        preference.onPreferenceChangeListener = sBindPreferenceSummaryToValueListener

        // Trigger the listener immediately with the preference's current value. Special handling
        // for directory pickers and master switches since they can't be converted to string
        val initialValue: Any? = when (preference) {
            is StoragePickerPreference -> PreferenceManager.getDefaultSharedPreferences(preference.context).getStringSet(preference.key, null)
            is MasterSwitchPreference -> PreferenceManager.getDefaultSharedPreferences(preference.context).getBoolean(preference.key, false)
            else -> PreferenceManager.getDefaultSharedPreferences(preference.context).getString(preference.key, null)
        }
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, initialValue)
    }

    fun setupStoragePickerPreference(storagePreference: StoragePickerPreference) {
        if (storagePreference.selectionType == StoragePickerPreference.SelectionType.FILE) {
            setupFilePickerPreference(storagePreference)
        } else {
            setupDirectoryPickerPreference(storagePreference)
        }
    }

    private fun setupDirectoryPickerPreference(storagePreference: StoragePickerPreference) {
        bindPreferenceSummaryToValue(storagePreference)
        val filePickerLauncher = activity.registerForActivityResult(DirectoryPickerContract(), storagePreference::onDirectoryPicked)
        storagePreference.setOnPreferenceClickListener { preference ->
            val initialUri = preference.getPersistedStringSet(null)?.firstOrNull()?.let { Uri.parse(it) }
            filePickerLauncher.launch(initialUri)
            true
        }
    }

    private fun setupFilePickerPreference(storagePreference: StoragePickerPreference) {
        bindPreferenceSummaryToValue(storagePreference)
        val filePickerLauncher = activity.registerForActivityResult(FilePickerContract(), storagePreference::onDirectoryPicked)
        storagePreference.setOnPreferenceClickListener { preference ->
            val initialUri = preference.getPersistedStringSet(null)?.firstOrNull()?.let { Uri.parse(it) }
            filePickerLauncher.launch(Pair(initialUri, storagePreference.mimeType))
            true
        }
    }
}