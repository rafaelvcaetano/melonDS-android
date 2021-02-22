package me.magnum.melonds.ui.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import me.magnum.melonds.R

class FirmwarePreferencesFragment : BasePreferencesFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_firmware_user_settings, rootKey)
        bindPreferenceSummaryToValue(findPreference("firmware_settings_birthday"))
    }

    override fun getTitle(): String {
        return getString(R.string.firmware_user_settings)
    }
}