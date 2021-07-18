package me.magnum.melonds.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.preference.*
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.common.contracts.FilePickerContract
import me.magnum.melonds.common.vibration.TouchVibrator
import me.magnum.melonds.domain.model.MicSource
import me.magnum.melonds.domain.model.SizeUnit
import me.magnum.melonds.extensions.isMicrophonePermissionGranted
import me.magnum.melonds.extensions.isSustainedPerformanceModeAvailable
import me.magnum.melonds.ui.inputsetup.InputSetupActivity
import me.magnum.melonds.ui.layouts.LayoutListActivity
import me.magnum.melonds.utils.enumValueOfIgnoreCase
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import kotlin.math.pow

@AndroidEntryPoint
class MainPreferencesFragment : PreferenceFragmentCompat(), PreferenceFragmentTitleProvider {
    private val viewModel: SettingsViewModel by activityViewModels()
    private val helper = PreferenceFragmentHelper(this)
    @Inject lateinit var vibrator: TouchVibrator

    private lateinit var clearRomCachePreference: Preference
    private lateinit var customBiosPreference: Preference
    private lateinit var micSourcePreference: ListPreference
    private val microphonePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            micSourcePreference.value = MicSource.DEVICE.name.toLowerCase(Locale.ROOT)
        }
    }
    private val filePickerLauncher = registerForActivityResult(FilePickerContract(false)) {
        if (it != null) {
            viewModel.importCheatsDatabase(it)
            CheatsImportProgressDialog().show(childFragmentManager, null)
        }
    }

    override fun getTitle() = getString(R.string.settings)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_main, rootKey)
        val sustainedPerformancePreference = findPreference<SwitchPreference>("enable_sustained_performance")!!
        val cacheSizePreference = findPreference<SeekBarPreference>("rom_cache_max_size")!!
        clearRomCachePreference = findPreference("rom_cache_clear")!!
        customBiosPreference = findPreference("use_custom_bios")!!
        val jitPreference = findPreference<SwitchPreference>("enable_jit")!!
        val touchVibratePreference = findPreference<SwitchPreference>("input_touch_haptic_feedback_enabled")!!
        val vibrationStrengthPreference = findPreference<SeekBarPreference>("input_touch_haptic_feedback_strength")!!
        val importCheatsPreference = findPreference<Preference>("cheats_import")!!
        micSourcePreference = findPreference("mic_source")!!
        val keyMappingPreference = findPreference<Preference>("input_key_mapping")!!
        val layoutsPreference = findPreference<Preference>("input_layouts")!!

        helper.setupStoragePickerPreference(findPreference("rom_search_dirs")!!)
        helper.setupStoragePickerPreference(findPreference("sram_dir")!!)
        helper.bindPreferenceSummaryToValue(customBiosPreference)

        sustainedPerformancePreference.isVisible = requireContext().isSustainedPerformanceModeAvailable()
        updateMaxCacheSizePreferenceSummary(cacheSizePreference, cacheSizePreference.value)

        if (Build.SUPPORTED_64_BIT_ABIS.isEmpty()) {
            jitPreference.isEnabled = false
            jitPreference.isChecked = false
            jitPreference.setSummary(R.string.jit_not_supported)
        }

        if (!vibrator.supportsVibration()) {
            touchVibratePreference.isVisible = false
            vibrationStrengthPreference.isVisible = false
        }

        cacheSizePreference.setOnPreferenceChangeListener { preference, newValue ->
            updateMaxCacheSizePreferenceSummary(preference as SeekBarPreference, newValue as Int)
            true
        }
        clearRomCachePreference.setOnPreferenceClickListener {
            if (!viewModel.clearRomCache()) {
                Toast.makeText(requireContext(), R.string.error_clear_rom_cache, Toast.LENGTH_LONG).show()
            }
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
        layoutsPreference.setOnPreferenceClickListener {
            val intent = Intent(requireContext(), LayoutListActivity::class.java)
            startActivity(intent)
            true
        }
        importCheatsPreference.setOnPreferenceClickListener {
            handleCheatsImport()
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.getRomCacheSize().observe(viewLifecycleOwner, {
            val cacheSizeRepresentation = getBestCacheSizeRepresentation(it)
            val sizeDecimal = BigDecimal(cacheSizeRepresentation.first).setScale(1, RoundingMode.HALF_EVEN)
            clearRomCachePreference.summary = getString(R.string.cache_size, "${sizeDecimal}${cacheSizeRepresentation.second}")
        })
    }

    override fun onResume() {
        super.onResume()
        // Set proper value for Custom BIOS option when returning from the fragment. Let's just pretend this is not here
        customBiosPreference.onPreferenceChangeListener.onPreferenceChange(customBiosPreference, customBiosPreference.sharedPreferences.getBoolean(customBiosPreference.key, false))
    }

    private fun updateMaxCacheSizePreferenceSummary(maxCacheSizePreference: SeekBarPreference, cacheSizeStep: Int) {
        val cacheSize = SizeUnit.MB(128) * 2.toDouble().pow(cacheSizeStep).toLong()
        val (sizeValue, sizeUnits) = getBestCacheSizeRepresentation(cacheSize)
        val sizeDecimal = BigDecimal(sizeValue).setScale(0, RoundingMode.HALF_EVEN)
        maxCacheSizePreference.summary = "${sizeDecimal}${sizeUnits}"
    }

    private fun getBestCacheSizeRepresentation(cacheSize: SizeUnit): Pair<Double, String> {
        return when(cacheSize.toBestRepresentation()) {
            is SizeUnit.Bytes -> cacheSize.toBytes().toDouble() to "B"
            is SizeUnit.KB -> cacheSize.toKB() to "KB"
            is SizeUnit.MB -> cacheSize.toMB() to "MB"
            is SizeUnit.GB -> cacheSize.toGB() to "GB"
        }
    }

    private fun requestMicrophonePermission(overrideRationaleRequest: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return

        if (!overrideRationaleRequest && shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            AlertDialog.Builder(requireContext())
                    .setTitle(R.string.microphone_permission_required)
                    .setMessage(R.string.microphone_permission_required_info)
                    .setPositiveButton(R.string.ok) { _, _ -> requestMicrophonePermission(true) }
                    .show()
        } else {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun handleCheatsImport() {
        if (viewModel.areCheatsBeingImported()) {
            CheatsImportProgressDialog().show(childFragmentManager, null)
        } else {
            filePickerLauncher.launch(Pair(null, arrayOf("text/xml")))
        }
    }
}