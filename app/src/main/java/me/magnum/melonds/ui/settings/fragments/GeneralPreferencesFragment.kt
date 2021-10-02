package me.magnum.melonds.ui.settings.fragments

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreference
import me.magnum.melonds.R
import me.magnum.melonds.common.UriPermissionManager
import me.magnum.melonds.domain.model.SizeUnit
import me.magnum.melonds.extensions.isSustainedPerformanceModeAvailable
import me.magnum.melonds.ui.settings.PreferenceFragmentHelper
import me.magnum.melonds.ui.settings.PreferenceFragmentTitleProvider
import me.magnum.melonds.ui.settings.SettingsViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import kotlin.math.pow

class GeneralPreferencesFragment : PreferenceFragmentCompat(), PreferenceFragmentTitleProvider {

    override fun getTitle() = getString(R.string.category_general)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_general, rootKey)

        val sustainedPerformancePreference = findPreference<SwitchPreference>("enable_sustained_performance")!!
        sustainedPerformancePreference.isVisible = requireContext().isSustainedPerformanceModeAvailable()
    }
}