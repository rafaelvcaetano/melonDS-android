package me.magnum.melonds.ui.settings

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.MicSource
import me.magnum.melonds.common.contracts.FilePickerContract
import me.magnum.melonds.utils.enumValueOfIgnoreCase
import me.magnum.melonds.utils.isMicrophonePermissionGranted
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

@AndroidEntryPoint
class MainPreferencesFragment : PreferenceFragmentCompat(), PreferenceFragmentTitleProvider {
    private val viewModel: SettingsViewModel by activityViewModels()
    private val helper = PreferenceFragmentHelper(this)

    private lateinit var clearRomCachePreference: Preference
    private lateinit var customBiosPreference: Preference
    private lateinit var micSourcePreference: ListPreference
    private val microphonePermissionLauncher by lazy {
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                micSourcePreference.value = MicSource.DEVICE.name.toLowerCase(Locale.ROOT)
            }
        }
    }

    override fun getTitle() = getString(R.string.settings)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_main, rootKey)
        clearRomCachePreference = findPreference("rom_cache_clear")!!
        customBiosPreference = findPreference("use_custom_bios")!!
        val jitPreference = findPreference<SwitchPreference>("enable_jit")!!
        val importCheatsPreference = findPreference<Preference>("cheats_import")!!
        micSourcePreference = findPreference("mic_source")!!

        helper.setupStoragePickerPreference(findPreference("rom_search_dirs")!!)
        helper.setupStoragePickerPreference(findPreference("sram_dir")!!)
        helper.bindPreferenceSummaryToValue(customBiosPreference)

        if (Build.SUPPORTED_64_BIT_ABIS.isEmpty()) {
            jitPreference.isEnabled = false
            jitPreference.isChecked = false
            jitPreference.setSummary(R.string.jit_not_supported)
        }

        val currentMicSource = enumValueOfIgnoreCase<MicSource>(micSourcePreference.value as String)
        if (currentMicSource == MicSource.DEVICE && !isMicrophonePermissionGranted(requireContext())) {
            micSourcePreference.value = MicSource.BLOW.name.toLowerCase(Locale.ROOT)
        }

        clearRomCachePreference.setOnPreferenceClickListener {
            if (!viewModel.clearRomCache()) {
                Toast.makeText(requireContext(), R.string.error_clear_rom_cache, Toast.LENGTH_LONG).show()
            }
            true
        }
        micSourcePreference.setOnPreferenceChangeListener { _, newValue ->
            val newMicSource = enumValueOfIgnoreCase<MicSource>(newValue as String)
            if (newMicSource == MicSource.DEVICE && !isMicrophonePermissionGranted(requireContext())) {
                requestMicrophonePermission(false)
                false
            } else {
                true
            }
        }
        importCheatsPreference.setOnPreferenceClickListener {
            handleCheatsImport()
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.getRomCacheSize().observe(viewLifecycleOwner, Observer {
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

    private fun getBestCacheSizeRepresentation(cacheSize: Long): Pair<Double, String> {
        if (cacheSize < 1024) return cacheSize.toDouble() to "B"
        if (cacheSize / 1024.0 < 1024.0) return cacheSize / 1024.0 to "KB"
        if (cacheSize / 1024.0 / 1024.0 < 1024.0) return cacheSize / 1024.0 / 1024.0 to "MB"
        return cacheSize / 1024.0 / 1024.0 / 1024.0 to "GB"
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
            val filePickerLauncher = registerForActivityResult(FilePickerContract(false)) {
                if (it != null) {
                    viewModel.importCheatsDatabase(it)
                    CheatsImportProgressDialog().show(childFragmentManager, null)
                }
            }
            filePickerLauncher.launch(Pair(null, "text/xml"))
        }
    }
}