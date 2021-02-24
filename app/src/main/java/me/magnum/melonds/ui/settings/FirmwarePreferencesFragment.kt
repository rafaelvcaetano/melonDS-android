package me.magnum.melonds.ui.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import me.magnum.melonds.R

class FirmwarePreferencesFragment : PreferenceFragmentCompat(), PreferenceFragmentTitleProvider {
    private val helper = PreferenceFragmentHelper(this)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_firmware_user_settings, rootKey)
        helper.bindPreferenceSummaryToValue(findPreference("firmware_settings_birthday"))
    }

    override fun getTitle() = getString(R.string.firmware_user_settings)
}