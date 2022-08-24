package me.magnum.melonds.ui.settings.fragments

import android.os.Build
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.common.DirectoryAccessValidator
import me.magnum.melonds.common.UriPermissionManager
import me.magnum.melonds.ui.settings.PreferenceFragmentHelper
import me.magnum.melonds.ui.settings.PreferenceFragmentTitleProvider
import javax.inject.Inject

@AndroidEntryPoint
class SystemPreferencesFragment : PreferenceFragmentCompat(), PreferenceFragmentTitleProvider {

    private val helper by lazy { PreferenceFragmentHelper(this, uriPermissionManager, directoryAccessValidator) }
    @Inject lateinit var uriPermissionManager: UriPermissionManager
    @Inject lateinit var directoryAccessValidator: DirectoryAccessValidator

    private lateinit var customBiosPreference: Preference

    override fun getTitle() = getString(R.string.category_system)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_system, rootKey)
        customBiosPreference = findPreference("use_custom_bios")!!
        val jitPreference = findPreference<SwitchPreference>("enable_jit")!!

        helper.bindPreferenceSummaryToValue(customBiosPreference)

        if (Build.SUPPORTED_64_BIT_ABIS.isEmpty()) {
            jitPreference.isEnabled = false
            jitPreference.isChecked = false
            jitPreference.setSummary(R.string.jit_not_supported)
        }
    }

    override fun onResume() {
        super.onResume()
        // Set proper value for Custom BIOS option when returning from the fragment. Let's just pretend this is not here
        customBiosPreference.onPreferenceChangeListener?.onPreferenceChange(customBiosPreference, customBiosPreference.sharedPreferences?.getBoolean(customBiosPreference.key, false))
    }
}