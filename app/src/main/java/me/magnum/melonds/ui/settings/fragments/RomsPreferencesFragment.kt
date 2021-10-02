package me.magnum.melonds.ui.settings.fragments

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.common.UriPermissionManager
import me.magnum.melonds.domain.model.SizeUnit
import me.magnum.melonds.ui.settings.PreferenceFragmentHelper
import me.magnum.melonds.ui.settings.PreferenceFragmentTitleProvider
import me.magnum.melonds.ui.settings.SettingsViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import kotlin.math.pow

@AndroidEntryPoint
class RomsPreferencesFragment : PreferenceFragmentCompat(), PreferenceFragmentTitleProvider {

    private val viewModel: SettingsViewModel by activityViewModels()
    private val helper by lazy { PreferenceFragmentHelper(this, uriPermissionManager) }
    @Inject lateinit var uriPermissionManager: UriPermissionManager

    private lateinit var clearRomCachePreference: Preference

    override fun getTitle() = getString(R.string.category_roms)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_roms, rootKey)
        val cacheSizePreference = findPreference<SeekBarPreference>("rom_cache_max_size")!!
        clearRomCachePreference = findPreference("rom_cache_clear")!!

        helper.setupStoragePickerPreference(findPreference("rom_search_dirs")!!)

        updateMaxCacheSizePreferenceSummary(cacheSizePreference, cacheSizePreference.value)

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
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.getRomCacheSize().observe(viewLifecycleOwner, {
            val cacheSizeRepresentation = getBestCacheSizeRepresentation(it)
            val sizeDecimal = BigDecimal(cacheSizeRepresentation.first).setScale(1, RoundingMode.HALF_EVEN)
            clearRomCachePreference.summary = getString(R.string.cache_size, "${sizeDecimal}${cacheSizeRepresentation.second}")
        })
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
}