package me.magnum.melonds.ui.settings.fragments

import android.content.Intent
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreference
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.common.vibration.TouchVibrator
import me.magnum.melonds.ui.inputsetup.InputSetupActivity
import me.magnum.melonds.ui.settings.PreferenceFragmentTitleProvider
import me.magnum.melonds.ui.settings.preferences.SoftwareInputBehaviourPreference
import javax.inject.Inject

@AndroidEntryPoint
class InputPreferencesFragment : PreferenceFragmentCompat(), PreferenceFragmentTitleProvider {

    @Inject lateinit var vibrator: TouchVibrator

    private lateinit var softInputBehaviourPreference: SoftwareInputBehaviourPreference

    override fun getTitle() = getString(R.string.input)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_input, rootKey)
        softInputBehaviourPreference = findPreference("soft_input_behaviour")!!
        val touchVibratePreference = findPreference<SwitchPreference>("input_touch_haptic_feedback_enabled")!!
        val vibrationStrengthPreference = findPreference<SeekBarPreference>("input_touch_haptic_feedback_strength")!!
        val keyMappingPreference = findPreference<Preference>("input_key_mapping")!!

        if (!vibrator.supportsVibration()) {
            touchVibratePreference.isVisible = false
        }
        vibrationStrengthPreference.isVisible = false

        vibrationStrengthPreference.setOnPreferenceChangeListener { _, newValue ->
            val strength = newValue as Int
            vibrator.performTouchHapticFeedback(strength)
            true
        }
        keyMappingPreference.setOnPreferenceClickListener {
            val intent = Intent(requireContext(), InputSetupActivity::class.java)
            startActivity(intent)
            true
        }
    }

    override fun onResume() {
        super.onResume()
        // Set proper value for soft input behaviour preference since the value is not updated when returning from the fragment
        softInputBehaviourPreference.value = softInputBehaviourPreference.sharedPreferences?.getString(softInputBehaviourPreference.key, "hide_system_buttons_when_controller_connected")
    }
}