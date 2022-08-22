package me.magnum.melonds.ui.settings.fragments

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import me.magnum.melonds.R
import me.magnum.melonds.common.Permission
import me.magnum.melonds.common.contracts.FilePickerContract
import me.magnum.melonds.extensions.isNotificationPermissionGranted
import me.magnum.melonds.ui.settings.CheatsImportProgressDialog
import me.magnum.melonds.ui.settings.PreferenceFragmentTitleProvider
import me.magnum.melonds.ui.settings.SettingsViewModel

class CheatsPreferencesFragment : PreferenceFragmentCompat(), PreferenceFragmentTitleProvider {

    private val viewModel: SettingsViewModel by activityViewModels()

    private val cheatFilePickerLauncher = registerForActivityResult(FilePickerContract(Permission.READ)) {
        if (it != null) {
            viewModel.importCheatsDatabase(it)
            CheatsImportProgressDialog().show(childFragmentManager, null)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        handleCheatsImport()
    }

    override fun getTitle() = getString(R.string.cheats)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_cheats, rootKey)
        val importCheatsPreference = findPreference<Preference>("cheats_import")!!

        importCheatsPreference.setOnPreferenceClickListener {
            if (!requestNotificationPermission()) {
                handleCheatsImport()
            }
            true
        }
    }

    private fun handleCheatsImport() {
        if (viewModel.areCheatsBeingImported()) {
            CheatsImportProgressDialog().show(childFragmentManager, null)
        } else {
            cheatFilePickerLauncher.launch(Pair(null, arrayOf("text/xml")))
        }
    }

    /**
     * Requests the notification permission if required.
     *
     * @return Whether the permission is being requested or not
     */
    private fun requestNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return false
        }

        return if (requireContext().isNotificationPermissionGranted()) {
            false
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            true
        }
    }
}