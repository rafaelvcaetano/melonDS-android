package me.magnum.melonds.ui.settings.fragments

import android.app.ActivityManager
import android.os.Bundle
import androidx.core.content.getSystemService
import androidx.preference.Preference
import androidx.preference.SeekBarPreference
import com.smp.masterswitchpreference.MasterSwitchPreferenceFragment
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.SizeUnit
import me.magnum.melonds.ui.settings.PreferenceFragmentTitleProvider
import me.magnum.melonds.utils.SizeUtils

class RewindPreferencesFragment : MasterSwitchPreferenceFragment(), PreferenceFragmentTitleProvider {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        val rewindPeriodPreference = findPreference<SeekBarPreference>("rewind_period")!!
        val rewindWindowPreference = findPreference<SeekBarPreference>("rewind_window")!!
        val rewindInfoPreference = findPreference<Preference>("rewind_info")!!

        val deviceMemory = requireContext().getSystemService<ActivityManager>()?.let {
            val memoryInfo = ActivityManager.MemoryInfo()
            it.getMemoryInfo(memoryInfo)
            SizeUnit.Bytes(memoryInfo.totalMem)
        }

        rewindPeriodPreference.setOnPreferenceChangeListener { _, newValue ->
            updateRewindInfo(rewindInfoPreference, newValue as Int, rewindWindowPreference.value, deviceMemory)
            updateRewindPeriodPreferenceSummary(rewindPeriodPreference, newValue)
            true
        }
        rewindWindowPreference.setOnPreferenceChangeListener { _, newValue ->
            updateRewindInfo(rewindInfoPreference, rewindPeriodPreference.value, newValue as Int, deviceMemory)
            updateRewindWindowPreferenceSummary(rewindWindowPreference, newValue)
            true
        }

        updateRewindPeriodPreferenceSummary(rewindPeriodPreference, rewindPeriodPreference.value)
        updateRewindWindowPreferenceSummary(rewindWindowPreference, rewindWindowPreference.value)
        updateRewindInfo(rewindInfoPreference, rewindPeriodPreference.value, rewindWindowPreference.value, deviceMemory)
    }

    private fun updateRewindPeriodPreferenceSummary(rewindPeriodPreference: SeekBarPreference, newValue: Int) {
        rewindPeriodPreference.summary = getString(R.string.rewind_time_seconds, newValue.toString())
    }

    private fun updateRewindWindowPreferenceSummary(rewindWindowPreference: SeekBarPreference, newValue: Int) {
        val newWindow = newValue * 10
        rewindWindowPreference.summary = getString(R.string.rewind_time_seconds, newWindow.toString())
    }

    private fun updateRewindInfo(rewindInfoPreference: Preference, rewindPeriodValue: Int, rewindWindowValue: Int, deviceMemory: SizeUnit?) {
        val rewindPeriod = rewindPeriodValue
        val rewindWindow = rewindWindowValue * 10
        val recommendedMaximumMemoryUsage = deviceMemory?.let { it * 0.2f }

        val maximumRewindStates = rewindWindow / rewindPeriod
        // Assume 20MB per save state
        val maximumMemoryUsage = SizeUnit.MB(20) * maximumRewindStates
        val memoryUsageRepresentation = SizeUtils.getBestSizeStringRepresentation(requireContext(), maximumMemoryUsage, 2)

        val summaryStringBuilder = StringBuilder()
        summaryStringBuilder.append(getString(R.string.rewind_max_memory_usage, memoryUsageRepresentation))

        if (recommendedMaximumMemoryUsage != null && maximumMemoryUsage > recommendedMaximumMemoryUsage) {
            summaryStringBuilder.appendLine()
            summaryStringBuilder.append(getString(R.string.rewind_memory_usage_above_recommended_limit))
        }

        rewindInfoPreference.summary = summaryStringBuilder.toString()
    }

    override fun getTitle() =  getString(R.string.rewind)
}