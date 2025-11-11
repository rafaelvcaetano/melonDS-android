package me.magnum.melonds.ui.settings.fragments

import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.content.getSystemService
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import me.magnum.melonds.domain.repositories.LayoutsRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.R
import me.magnum.melonds.common.DirectoryAccessValidator
import me.magnum.melonds.common.UriPermissionManager
import me.magnum.melonds.domain.model.VideoRenderer
import me.magnum.melonds.domain.model.DualScreenPreset
import me.magnum.melonds.domain.model.DsExternalScreen
import me.magnum.melonds.domain.model.camera.DSiCameraSourceType
import me.magnum.melonds.ui.settings.PreferenceFragmentHelper
import me.magnum.melonds.ui.settings.PreferenceFragmentTitleProvider
import me.magnum.melonds.ui.settings.preferences.StoragePickerPreference
import me.magnum.melonds.ui.layouts.ExternalLayoutListActivity
import me.magnum.melonds.utils.enumValueOfIgnoreCase
import me.magnum.melonds.ui.layouts.LayoutListActivity
import androidx.appcompat.app.AlertDialog
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.isVisible
import javax.inject.Inject

@AndroidEntryPoint
class VideoPreferencesFragment : PreferenceFragmentCompat(), PreferenceFragmentTitleProvider {

    private companion object {
        const val GLES_3_2 = 0x30002
    }

    private val helper by lazy { PreferenceFragmentHelper(this, uriPermissionManager, directoryAccessValidator) }
    @Inject lateinit var uriPermissionManager: UriPermissionManager
    @Inject lateinit var directoryAccessValidator: DirectoryAccessValidator
    @Inject lateinit var layoutsRepository: LayoutsRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    private val softwareRendererPreferences = mutableListOf<Preference>()
    private val openGlRendererPreferences = mutableListOf<Preference>()

    private lateinit var externalLayoutsPreference: Preference
    private lateinit var internalLayoutsPreference: Preference
    private lateinit var dualScreenPresetsPreference: Preference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_video, rootKey)

        softwareRendererPreferences.apply {
            add(findPreference("enable_threaded_rendering")!!)
        }

        openGlRendererPreferences.apply {
            add(findPreference("video_internal_resolution")!!)
        }

        val rendererPreference = findPreference<ListPreference>("video_renderer")!!
        val videoFilteringPreference = findPreference<ListPreference>("video_filtering")!!
        val dsiCameraSourcePreference = findPreference<ListPreference>("dsi_camera_source")!!
        val dsiCameraImagePreference = findPreference<StoragePickerPreference>("dsi_camera_static_image")!!
        val customShaderPreference = findPreference<StoragePickerPreference>("video_custom_shader")!!
        externalLayoutsPreference = findPreference("external_layouts")!!
        internalLayoutsPreference = findPreference("input_layouts")!!
        dualScreenPresetsPreference = findPreference("dual_screen_presets")!!

        val activityManager = requireContext().getSystemService<ActivityManager>()

        rendererPreference.apply {
            val deviceGlesVersion = activityManager?.deviceConfigurationInfo?.reqGlEsVersion ?: 0
            if (deviceGlesVersion >= GLES_3_2) {
                setOnPreferenceChangeListener { _, newValue ->
                    onRendererPreferenceChanged(newValue as String)
                    true
                }
            } else {
                // GLES 3.2 is not supported. Remove the preference
                isVisible = false
            }
        }

        dsiCameraSourcePreference.setOnPreferenceChangeListener { _, newValue ->
            updateDsiCameraImagePreference(dsiCameraImagePreference, newValue as String)
            true
        }

        externalLayoutsPreference.setOnPreferenceClickListener {
            val intent = Intent(requireContext(), ExternalLayoutListActivity::class.java)
            startActivity(intent)
            true
        }

        internalLayoutsPreference.setOnPreferenceClickListener {
            val intent = Intent(requireContext(), LayoutListActivity::class.java)
            startActivity(intent)
            true
        }

        dualScreenPresetsPreference.setOnPreferenceClickListener {
            showDualScreenPresetsDialog()
            true
        }

        helper.setupStoragePickerPreference(dsiCameraImagePreference)
        helper.setupStoragePickerPreference(customShaderPreference)

        customShaderPreference.isEnabled = videoFilteringPreference.value == "custom"
        videoFilteringPreference.setOnPreferenceChangeListener { _, newValue ->
            customShaderPreference.isEnabled = (newValue as String) == "custom"
            true
        }

        onRendererPreferenceChanged(rendererPreference.value)
        updateDsiCameraImagePreference(dsiCameraImagePreference, dsiCameraSourcePreference.value)
        lifecycleScope.launch { updateLayoutPreferenceSummaries() }
        updateDualScreenPresetSummary()
    }

    private fun onRendererPreferenceChanged(rendererValue: String) {
        val newRenderer = enumValueOfIgnoreCase<VideoRenderer>(rendererValue)
        when (newRenderer) {
            VideoRenderer.SOFTWARE -> {
                softwareRendererPreferences.forEach {
                    it.isVisible = true
                }
                openGlRendererPreferences.forEach {
                    it.isVisible = false
                }
            }
            VideoRenderer.OPENGL -> {
                softwareRendererPreferences.forEach {
                    it.isVisible = false
                }
                openGlRendererPreferences.forEach {
                    it.isVisible = true
                }
            }
        }
    }

    private fun updateDsiCameraImagePreference(preference: StoragePickerPreference, dsiCameraSourceValue: String) {
        val newSource = enumValueOfIgnoreCase<DSiCameraSourceType>(dsiCameraSourceValue)
        preference.isEnabled = newSource == DSiCameraSourceType.STATIC_IMAGE
    }

    override fun onResume() {
        super.onResume()
        viewLifecycleOwner.lifecycleScope.launch { updateLayoutPreferenceSummaries() }
        updateDualScreenPresetSummary()
    }

    private suspend fun updateLayoutPreferenceSummaries() {
        val internalLayoutId = settingsRepository.getSelectedLayoutId()
        val externalLayoutId = settingsRepository.getExternalLayoutId()
        val internalLayout = layoutsRepository.getLayout(internalLayoutId) ?: layoutsRepository.getGlobalLayoutPlaceholder()
        val externalLayout = layoutsRepository.getLayout(externalLayoutId) ?: layoutsRepository.getGlobalLayoutPlaceholder()
        internalLayoutsPreference.summary = internalLayout.name
        externalLayoutsPreference.summary = externalLayout.name
        updateDualScreenPresetSummary()
    }

    private fun updateDualScreenPresetSummary() {
        if (!this::dualScreenPresetsPreference.isInitialized) {
            return
        }
        val preset = settingsRepository.getDualScreenPreset()
        val keepAspect = settingsRepository.isExternalDisplayKeepAspectRationEnabled()
        val integerScale = settingsRepository.isDualScreenIntegerScaleEnabled() && preset != DualScreenPreset.OFF
        val fillModesActive = preset != DualScreenPreset.OFF && (integerScale || keepAspect)
        val internalFillHeight = settingsRepository.isDualScreenInternalFillHeightEnabled() && fillModesActive
        val internalFillWidth = settingsRepository.isDualScreenInternalFillWidthEnabled() && fillModesActive
        val externalFillHeight = settingsRepository.isDualScreenExternalFillHeightEnabled() && fillModesActive
        val externalFillWidth = settingsRepository.isDualScreenExternalFillWidthEnabled() && fillModesActive

        val presetTextRes = when (preset) {
            DualScreenPreset.OFF -> R.string.dual_screen_preset_off
            DualScreenPreset.INTERNAL_TOP_EXTERNAL_BOTTOM -> R.string.dual_screen_preset_internal_top_external_bottom
            DualScreenPreset.INTERNAL_BOTTOM_EXTERNAL_TOP -> R.string.dual_screen_preset_internal_bottom_external_top
        }

        dualScreenPresetsPreference.summary = getString(
            R.string.dual_screen_presets_summary,
            getString(presetTextRes),
            if (keepAspect) getString(R.string.on) else getString(R.string.off),
            if (preset == DualScreenPreset.OFF) getString(R.string.off) else if (integerScale) getString(R.string.on) else getString(R.string.off),
            if (internalFillHeight) getString(R.string.on) else getString(R.string.off),
            if (internalFillWidth) getString(R.string.on) else getString(R.string.off),
            if (externalFillHeight) getString(R.string.on) else getString(R.string.off),
            if (externalFillWidth) getString(R.string.on) else getString(R.string.off),
        )
    }

    private fun showDualScreenPresetsDialog() {
        val currentPreset = settingsRepository.getDualScreenPreset()
        val keepAspectRatioInitial = settingsRepository.isExternalDisplayKeepAspectRationEnabled()
        val integerScaleInitial = settingsRepository.isDualScreenIntegerScaleEnabled()
        val internalFillInitial = settingsRepository.isDualScreenInternalFillHeightEnabled()
        val internalFillWidthInitial = settingsRepository.isDualScreenInternalFillWidthEnabled()
        val externalFillInitial = settingsRepository.isDualScreenExternalFillHeightEnabled()
        val externalFillWidthInitial = settingsRepository.isDualScreenExternalFillWidthEnabled()

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_dual_screen_presets, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroupPresets)
        val keepAspectSwitch = dialogView.findViewById<SwitchCompat>(R.id.switchKeepAspectRatio)
        val integerScaleSwitch = dialogView.findViewById<SwitchCompat>(R.id.switchIntegerScale)
        val fillAreaButton = dialogView.findViewById<Button>(R.id.buttonFillAreaOptions)
        val fillAreaSummary = dialogView.findViewById<TextView>(R.id.textFillAreaSummary)

        val presetToButtonId = mapOf(
            DualScreenPreset.OFF to R.id.radioPresetOff,
            DualScreenPreset.INTERNAL_TOP_EXTERNAL_BOTTOM to R.id.radioPresetInternalTopExternalBottom,
            DualScreenPreset.INTERNAL_BOTTOM_EXTERNAL_TOP to R.id.radioPresetInternalBottomExternalTop,
        )
        var selectedPreset = currentPreset
        var keepAspectRatio = keepAspectRatioInitial
        var integerScale = integerScaleInitial && currentPreset != DualScreenPreset.OFF
        var internalFill = internalFillInitial
        var internalFillWidth = internalFillWidthInitial
        var externalFill = externalFillInitial
        var externalFillWidth = externalFillWidthInitial

        radioGroup.check(presetToButtonId.getValue(currentPreset))
        keepAspectSwitch.isChecked = keepAspectRatio
        integerScaleSwitch.isChecked = integerScale
        integerScaleSwitch.isEnabled = currentPreset != DualScreenPreset.OFF

        fun updateFillAreaSummary() {
            fillAreaSummary.text = getString(
                R.string.dual_screen_fill_area_summary,
                if (internalFill) getString(R.string.on) else getString(R.string.off),
                if (internalFillWidth) getString(R.string.on) else getString(R.string.off),
                if (externalFill) getString(R.string.on) else getString(R.string.off),
                if (externalFillWidth) getString(R.string.on) else getString(R.string.off),
            )
            fillAreaSummary.isVisible = true
        }

        fun updateFillButtonState() {
            val enabled = selectedPreset != DualScreenPreset.OFF && (integerScale || keepAspectRatio)
            fillAreaButton.isEnabled = enabled
        }
        updateFillAreaSummary()
        updateFillButtonState()

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedPreset = presetToButtonId.entries.first { it.value == checkedId }.key
            val integerScaleEnabled = selectedPreset != DualScreenPreset.OFF
            integerScaleSwitch.isEnabled = integerScaleEnabled
            if (!integerScaleEnabled) {
                integerScaleSwitch.isChecked = false
                integerScale = false
            }
            updateFillButtonState()
        }

        keepAspectSwitch.setOnCheckedChangeListener { _, isChecked ->
            keepAspectRatio = isChecked
            updateFillButtonState()
        }

        integerScaleSwitch.setOnCheckedChangeListener { _, isChecked ->
            integerScale = isChecked
            updateFillButtonState()
        }

        fillAreaButton.setOnClickListener {
            val fillOptionsEnabled = selectedPreset != DualScreenPreset.OFF && (integerScale || keepAspectRatio)
            showFillAreaOptionsDialog(
                fillOptionsEnabled = fillOptionsEnabled,
                initialInternalFillHeight = internalFill,
                initialInternalFillWidth = internalFillWidth,
                initialExternalFillHeight = externalFill,
                initialExternalFillWidth = externalFillWidth,
            ) { newInternalFill, newInternalFillWidth, newExternalFill, newExternalFillWidth ->
                internalFill = newInternalFill
                internalFillWidth = newInternalFillWidth
                externalFill = newExternalFill
                externalFillWidth = newExternalFillWidth
                updateFillAreaSummary()
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dual_screen_presets_settings_title)
            .setView(dialogView)
            .setPositiveButton(R.string.ok) { _, _ ->
                settingsRepository.setDualScreenPreset(selectedPreset)
                when (selectedPreset) {
                    DualScreenPreset.INTERNAL_TOP_EXTERNAL_BOTTOM -> settingsRepository.setExternalDisplayScreen(DsExternalScreen.BOTTOM)
                    DualScreenPreset.INTERNAL_BOTTOM_EXTERNAL_TOP -> settingsRepository.setExternalDisplayScreen(DsExternalScreen.TOP)
                    DualScreenPreset.OFF -> { /* Keep existing selection so manual layouts still work */ }
                }
                settingsRepository.setExternalDisplayKeepAspectRatioEnabled(keepAspectRatio)
                settingsRepository.setDualScreenIntegerScaleEnabled(integerScale && selectedPreset != DualScreenPreset.OFF)
                settingsRepository.setDualScreenInternalFillHeightEnabled(internalFill)
                settingsRepository.setDualScreenInternalFillWidthEnabled(internalFillWidth)
                settingsRepository.setDualScreenExternalFillHeightEnabled(externalFill)
                settingsRepository.setDualScreenExternalFillWidthEnabled(externalFillWidth)
                updateDualScreenPresetSummary()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showFillAreaOptionsDialog(
        fillOptionsEnabled: Boolean,
        initialInternalFillHeight: Boolean,
        initialInternalFillWidth: Boolean,
        initialExternalFillHeight: Boolean,
        initialExternalFillWidth: Boolean,
        onValuesConfirmed: (Boolean, Boolean, Boolean, Boolean) -> Unit,
    ) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_dual_screen_fill_area, null)
        val description = dialogView.findViewById<TextView>(R.id.textFillAreaDescription)
        val disabledText = dialogView.findViewById<TextView>(R.id.textFillAreaDisabled)
        val internalHeightSwitch = dialogView.findViewById<SwitchCompat>(R.id.switchInternalFillHeight)
        val internalWidthSwitch = dialogView.findViewById<SwitchCompat>(R.id.switchInternalFillWidth)
        val externalHeightSwitch = dialogView.findViewById<SwitchCompat>(R.id.switchExternalFillHeight)
        val externalWidthSwitch = dialogView.findViewById<SwitchCompat>(R.id.switchExternalFillWidth)

        description.text = getString(R.string.dual_screen_fill_area_description)
        internalHeightSwitch.isChecked = initialInternalFillHeight
        internalWidthSwitch.isChecked = initialInternalFillWidth
        externalHeightSwitch.isChecked = initialExternalFillHeight
        externalWidthSwitch.isChecked = initialExternalFillWidth

        internalHeightSwitch.isEnabled = fillOptionsEnabled
        internalWidthSwitch.isEnabled = fillOptionsEnabled
        externalHeightSwitch.isEnabled = fillOptionsEnabled
        externalWidthSwitch.isEnabled = fillOptionsEnabled
        disabledText.isVisible = !fillOptionsEnabled

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dual_screen_fill_area_title)
            .setView(dialogView)
            .setPositiveButton(R.string.ok) { _, _ ->
                onValuesConfirmed(
                    internalHeightSwitch.isChecked,
                    internalWidthSwitch.isChecked,
                    externalHeightSwitch.isChecked,
                    externalWidthSwitch.isChecked,
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun getTitle(): String {
        return getString(R.string.category_video)
    }
}
