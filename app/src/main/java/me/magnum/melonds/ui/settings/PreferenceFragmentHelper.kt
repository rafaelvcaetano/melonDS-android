package me.magnum.melonds.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.smp.masterswitchpreference.MasterSwitchPreference
import me.magnum.melonds.R
import me.magnum.melonds.common.DirectoryAccessValidator
import me.magnum.melonds.common.UriPermissionManager
import me.magnum.melonds.ui.settings.preferences.FirmwareBirthdayPreference
import me.magnum.melonds.ui.settings.preferences.StoragePickerPreference
import me.magnum.melonds.common.contracts.DirectoryPickerContract
import me.magnum.melonds.common.contracts.FilePickerContract
import me.magnum.melonds.extensions.addOnPreferenceChangeListener
import me.magnum.melonds.ui.settings.preferences.MacAddressPreference
import me.magnum.melonds.utils.FileUtils

class PreferenceFragmentHelper(
    private val fragment: PreferenceFragmentCompat,
    private val uriPermissionManager: UriPermissionManager,
    private val directoryAccessValidator: DirectoryAccessValidator
) {

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
                        preference.context.getString(R.string.not_set)
                    preference.setSummary(summary)
                }
                is StoragePickerPreference -> {
                    if (value == null || value !is Set<*> || value.isEmpty())
                        preference.summary = preference.getContext().getString(R.string.not_set)
                    else {
                        val uris = value.mapNotNull { FileUtils.getAbsolutePathFromSAFUri(preference.context, (it as String).toUri()) }
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
                is MacAddressPreference -> {
                    val addressString = value as String?
                    preference.summary = addressString ?: preference.context.getString(R.string.not_set)
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
        preference.addOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener)

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
        val filePickerLauncher = fragment.registerForActivityResult(DirectoryPickerContract(storagePreference.permissions), ActivityResultCallback {
            if (it == null) {
                return@ActivityResultCallback
            }

            // Validate directory access before update preference
            if (directoryAccessValidator.getDirectoryAccessForPermission(it, storagePreference.permissions) == DirectoryAccessValidator.DirectoryAccessResult.OK) {
                storagePreference.onDirectoryPicked(it)
            } else {
                showInvalidDirectoryAccessDialog()
            }
        })
        storagePreference.setOnPreferenceClickListener { preference ->
            val directories = preference.getPersistedStringSet(emptySet())?.toSet() ?: emptySet()
            val isMultiSelection = storagePreference.multiSelection

            if (isMultiSelection && directories.isNotEmpty()) {
                showDirectoryManagementDialog(storagePreference, directories, filePickerLauncher)
            } else {
                val initialUri = directories.firstOrNull()?.toUri()
                filePickerLauncher.launch(initialUri)
            }
            true
        }
        if (storagePreference.persistPermissions) {
            storagePreference.addOnPreferenceChangeListener { _, newValue ->
                (newValue as? Set<String>)?.forEach {
                    uriPermissionManager.persistDirectoryPermissions(it.toUri(), storagePreference.permissions)
                }
                false
            }
        }
    }

    private fun setupFilePickerPreference(storagePreference: StoragePickerPreference) {
        bindPreferenceSummaryToValue(storagePreference)
        val filePickerLauncher = fragment.registerForActivityResult(FilePickerContract(storagePreference.permissions), storagePreference::onDirectoryPicked)
        storagePreference.setOnPreferenceClickListener { preference ->
            val initialUri = preference.getPersistedStringSet(null)?.firstOrNull()?.toUri()
            filePickerLauncher.launch(Pair(initialUri, storagePreference.mimeTypes?.toTypedArray()))
            true
        }
        if (storagePreference.persistPermissions) {
            storagePreference.addOnPreferenceChangeListener { _, newValue ->
                (newValue as? Set<String>)?.firstOrNull()?.let {
                    uriPermissionManager.persistFilePermissions(it.toUri(), storagePreference.permissions)
                }
                true
            }
        }
    }

    private fun showInvalidDirectoryAccessDialog() {
        AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.error_invalid_directory)
            .setMessage(R.string.error_invalid_directory_description)
            .setPositiveButton(R.string.ok, null)
            .setCancelable(true)
            .show()
    }

    private fun showDirectoryManagementDialog(
        storagePreference: StoragePickerPreference,
        directories: Set<String>,
        filePickerLauncher: ActivityResultLauncher<Uri?>
    ) {
        val directoryList = directories.toList()
        val displayNames = directoryList.map { getDirectoryDisplayName(storagePreference.context, it) }

        AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.manage_rom_directories)
            .setItems(displayNames.toTypedArray()) { _, index ->
                val uriString = directoryList[index]
                promptRemoveDirectory(storagePreference, uriString)
            }
            .setPositiveButton(R.string.add_directory) { _, _ ->
                val initialUri = directoryList.firstOrNull()?.toUri()
                filePickerLauncher.launch(initialUri)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun promptRemoveDirectory(storagePreference: StoragePickerPreference, uriString: String) {
        val directoryName = getDirectoryDisplayName(storagePreference.context, uriString)
        AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.remove_rom_directory_title)
            .setMessage(fragment.getString(R.string.remove_rom_directory_message, directoryName))
            .setPositiveButton(R.string.action_remove) { _, _ ->
                removeDirectoryFromPreference(storagePreference, uriString)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun removeDirectoryFromPreference(storagePreference: StoragePickerPreference, uriString: String) {
        val currentDirectories = storagePreference.getPersistedStringSet(emptySet())?.toMutableSet() ?: mutableSetOf()
        if (currentDirectories.remove(uriString)) {
            storagePreference.updatePersistedDirectories(currentDirectories)
        }
    }

    private fun getDirectoryDisplayName(context: Context, uriString: String): String {
        val uri = uriString.toUri()
        return FileUtils.getAbsolutePathFromSAFUri(context, uri)
            ?: DocumentFile.fromTreeUri(context, uri)?.name
            ?: uri.lastPathSegment
            ?: uri.toString()
    }
}
