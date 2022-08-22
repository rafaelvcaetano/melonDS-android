package me.magnum.melonds.ui.settings.fragments

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.MicSource
import me.magnum.melonds.extensions.isMicrophonePermissionGranted
import me.magnum.melonds.ui.settings.PreferenceFragmentTitleProvider
import me.magnum.melonds.utils.enumValueOfIgnoreCase

class AudioPreferencesFragment : PreferenceFragmentCompat(), PreferenceFragmentTitleProvider {

    private lateinit var micSourcePreference: ListPreference

    private val microphonePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            micSourcePreference.value = MicSource.DEVICE.name.lowercase()
        }
    }

    override fun getTitle() = getString(R.string.category_audio)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_audio, rootKey)
        val volumePreference = findPreference<SeekBarPreference>("volume")!!
        micSourcePreference = findPreference("mic_source")!!

        updateVolumePreferenceSummary(volumePreference, volumePreference.value)

        volumePreference.setOnPreferenceChangeListener { _, newValue ->
            updateVolumePreferenceSummary(volumePreference, newValue as Int)
            true
        }
        micSourcePreference.setOnPreferenceChangeListener { _, newValue ->
            val newMicSource = enumValueOfIgnoreCase<MicSource>(newValue as String)
            if (newMicSource == MicSource.DEVICE && !requireContext().isMicrophonePermissionGranted()) {
                requestMicrophonePermission(false)
                false
            } else {
                true
            }
        }
    }

    private fun updateVolumePreferenceSummary(volumePreference: SeekBarPreference, volume: Int) {
        val volumePercentage = (volume / volumePreference.max.toFloat() * 100f).toInt()
        volumePreference.summary = getString(R.string.volume_percentage, volumePercentage)
    }

    private fun requestMicrophonePermission(overrideRationaleRequest: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return

        if (!overrideRationaleRequest && shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.microphone_permission_required)
                .setMessage(R.string.microphone_permission_required_info)
                .setPositiveButton(R.string.ok) { _, _ -> requestMicrophonePermission(true) }
                .show()
        } else {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}