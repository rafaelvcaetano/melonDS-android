package me.magnum.melonds.ui.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import me.magnum.melonds.R

class FirmwarePreferencesFragment : PreferenceFragmentCompat(), PreferenceFragmentTitleProvider {
    private val helper = PreferenceFragmentHelper(this)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_internal_firmware_settings, rootKey)
        helper.bindPreferenceSummaryToValue(findPreference("firmware_settings_birthday"))
        helper.bindPreferenceSummaryToValue(findPreference("internal_mac_address"))
    }

    override fun getTitle() = getString(R.string.internal_firmware_settings)
}