package me.magnum.melonds.ui.settings.fragments

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.smp.masterswitchpreference.MasterSwitchPreference
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.common.DirectoryAccessValidator
import me.magnum.melonds.common.UriPermissionManager
import me.magnum.melonds.extensions.isSustainedPerformanceModeAvailable
import me.magnum.melonds.ui.settings.PreferenceFragmentHelper
import me.magnum.melonds.ui.settings.PreferenceFragmentTitleProvider
import javax.inject.Inject

@AndroidEntryPoint
class GeneralPreferencesFragment : PreferenceFragmentCompat(), PreferenceFragmentTitleProvider {

    private val helper by lazy { PreferenceFragmentHelper(this, uriPermissionManager, directoryAccessValidator) }
    @Inject lateinit var uriPermissionManager: UriPermissionManager
    @Inject lateinit var directoryAccessValidator: DirectoryAccessValidator

    private lateinit var rewindPreference: MasterSwitchPreference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_general, rootKey)

        rewindPreference = findPreference("enable_rewind")!!
        val sustainedPerformancePreference = findPreference<SwitchPreference>("enable_sustained_performance")!!

        helper.bindPreferenceSummaryToValue(rewindPreference)
        sustainedPerformancePreference.isVisible = requireContext().isSustainedPerformanceModeAvailable()
    }

    override fun onResume() {
        super.onResume()
        // Set proper value for Rewind preference since the value is not updated when returning from the fragment
        rewindPreference.onPreferenceChangeListener?.onPreferenceChange(rewindPreference, rewindPreference.sharedPreferences?.getBoolean(rewindPreference.key, false))
    }

    override fun getTitle() = getString(R.string.category_general)
}