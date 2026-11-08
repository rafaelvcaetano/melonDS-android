package me.magnum.melonds.ui.settings.fragments

import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.common.Permission
import me.magnum.melonds.common.contracts.FilePickerContract
import me.magnum.melonds.ui.settings.CheatsImportProgressDialog
import me.magnum.melonds.ui.settings.PreferenceFragmentTitleProvider
import me.magnum.melonds.ui.settings.viewmodel.CheatsPreferencesViewModel

@AndroidEntryPoint
class CheatsPreferencesFragment : BasePreferenceFragment(), PreferenceFragmentTitleProvider {

    private val viewModel by viewModels<CheatsPreferencesViewModel>()

    private val cheatFilePickerLauncher = registerForActivityResult(FilePickerContract(Permission.READ)) {
        if (it != null) {
            viewModel.importCheatsDatabase(it)
            CheatsImportProgressDialog().show(childFragmentManager, null)
        }
    }

    override fun getTitle() = getString(R.string.cheats)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_cheats, rootKey)
        val importCheatsPreference = findPreference<Preference>("cheats_import")!!

        importCheatsPreference.setOnPreferenceClickListener {
            handleCheatsImport()
            true
        }
    }

    private fun handleCheatsImport() {
        if (viewModel.areCheatsBeingImported()) {
            CheatsImportProgressDialog().show(childFragmentManager, null)
        } else {
            cheatFilePickerLauncher.launch(Pair(null, arrayOf("text/xml", "application/octet-stream")))
        }
    }
}