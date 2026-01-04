package me.magnum.melonds.ui.settings.fragments

import android.app.ActivityManager
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.content.getSystemService
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.R
import me.magnum.melonds.common.DirectoryAccessValidator
import me.magnum.melonds.common.UriPermissionManager
import me.magnum.melonds.domain.model.VideoRenderer
import me.magnum.melonds.domain.model.DualScreenPreset
import me.magnum.melonds.domain.model.ScreenAlignment
import me.magnum.melonds.domain.model.camera.DSiCameraSourceType
import me.magnum.melonds.domain.model.defaultExternalAlignment
import me.magnum.melonds.domain.model.defaultInternalAlignment
import me.magnum.melonds.ui.settings.PreferenceFragmentHelper
import me.magnum.melonds.ui.settings.PreferenceFragmentTitleProvider
import me.magnum.melonds.ui.settings.preferences.StoragePickerPreference
import me.magnum.melonds.utils.enumValueOfIgnoreCase
import androidx.appcompat.app.AlertDialog
import android.widget.Button
import android.widget.RadioButton
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
    @Inject lateinit var settingsRepository: SettingsRepository

    private val softwareRendererPreferences = mutableListOf<Preference>()
    private val openGlRendererPreferences = mutableListOf<Preference>()

    private lateinit var dualScreenPresetsPreference: Preference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_video, rootKey)

        softwareRendererPreferences.apply {
            add(findPreference("enable_threaded_rendering")!!)
        }

        openGlRendererPreferences.apply {
            add(findPreference("video_internal_resolution")!!)
            add(findPreference("video_hacks_category")!!)
            add(findPreference("video_conservative_coverage_enabled")!!)
            add(findPreference("video_conservative_coverage_px")!!)
            add(findPreference("video_conservative_coverage_apply_repeat")!!)
            add(findPreference("video_conservative_coverage_apply_clamp")!!)
            add(findPreference("video_conservative_coverage_depth_bias")!!)
            add(findPreference("video_debug_3d_clear_magenta")!!)
        }

        val rendererPreference = findPreference<ListPreference>("video_renderer")!!
        val videoFilteringPreference = findPreference<ListPreference>("video_filtering")!!
        val dsiCameraSourcePreference = findPreference<ListPreference>("dsi_camera_source")!!
        val dsiCameraImagePreference = findPreference<StoragePickerPreference>("dsi_camera_static_image")!!
        val customShaderPreference = findPreference<StoragePickerPreference>("video_custom_shader")!!
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
        val internalAlignmentInitial = settingsRepository.getDualScreenInternalVerticalAlignmentOverride()
        val externalAlignmentInitial = settingsRepository.getDualScreenExternalVerticalAlignmentOverride()

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_dual_screen_presets, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroupPresets)
        val keepAspectSwitch = dialogView.findViewById<SwitchCompat>(R.id.switchKeepAspectRatio)
        val integerScaleSwitch = dialogView.findViewById<SwitchCompat>(R.id.switchIntegerScale)
        val fillAreaButton = dialogView.findViewById<Button>(R.id.buttonFillAreaOptions)
        val verticalAlignmentButton = dialogView.findViewById<Button>(R.id.buttonVerticalAlignmentOptions)
        val verticalAlignmentSummary = dialogView.findViewById<TextView>(R.id.textVerticalAlignmentSummary)

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
        var internalAlignmentOverride = internalAlignmentInitial
        var externalAlignmentOverride = externalAlignmentInitial

        radioGroup.check(presetToButtonId.getValue(currentPreset))
        keepAspectSwitch.isChecked = keepAspectRatio
        integerScaleSwitch.isChecked = integerScale
        integerScaleSwitch.isEnabled = currentPreset != DualScreenPreset.OFF

        fun updateDualScreenButtonsState() {
            val enabled = selectedPreset != DualScreenPreset.OFF && (integerScale || keepAspectRatio)
            fillAreaButton.isEnabled = enabled
            verticalAlignmentButton.isEnabled = enabled
        }
        fun updateVerticalAlignmentSummary() {
            verticalAlignmentSummary.text = getVerticalAlignmentSummary(selectedPreset, internalAlignmentOverride, externalAlignmentOverride)
            verticalAlignmentSummary.isVisible = true
        }
        updateVerticalAlignmentSummary()
        updateDualScreenButtonsState()

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedPreset = presetToButtonId.entries.first { it.value == checkedId }.key
            val integerScaleEnabled = selectedPreset != DualScreenPreset.OFF
            integerScaleSwitch.isEnabled = integerScaleEnabled
            if (!integerScaleEnabled) {
                integerScaleSwitch.isChecked = false
                integerScale = false
            }
            updateDualScreenButtonsState()
            updateVerticalAlignmentSummary()
        }

        keepAspectSwitch.setOnCheckedChangeListener { _, isChecked ->
            keepAspectRatio = isChecked
            updateDualScreenButtonsState()
        }

        integerScaleSwitch.setOnCheckedChangeListener { _, isChecked ->
            integerScale = isChecked
            updateDualScreenButtonsState()
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
            }
        }

        verticalAlignmentButton.setOnClickListener {
            showVerticalAlignmentOptionsDialog(
                preset = selectedPreset,
                initialInternalAlignment = internalAlignmentOverride,
                initialExternalAlignment = externalAlignmentOverride,
            ) { newInternalAlignment, newExternalAlignment ->
                internalAlignmentOverride = newInternalAlignment
                externalAlignmentOverride = newExternalAlignment
                updateVerticalAlignmentSummary()
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dual_screen_presets_settings_title)
            .setView(dialogView)
            .setPositiveButton(R.string.ok) { _, _ ->
                settingsRepository.setDualScreenPreset(selectedPreset)
                settingsRepository.setExternalDisplayKeepAspectRatioEnabled(keepAspectRatio)
                settingsRepository.setDualScreenIntegerScaleEnabled(integerScale && selectedPreset != DualScreenPreset.OFF)
                settingsRepository.setDualScreenInternalFillHeightEnabled(internalFill)
                settingsRepository.setDualScreenInternalFillWidthEnabled(internalFillWidth)
                settingsRepository.setDualScreenExternalFillHeightEnabled(externalFill)
                settingsRepository.setDualScreenExternalFillWidthEnabled(externalFillWidth)
                settingsRepository.setDualScreenInternalVerticalAlignmentOverride(internalAlignmentOverride)
                settingsRepository.setDualScreenExternalVerticalAlignmentOverride(externalAlignmentOverride)
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
        val internalEnabledSwitch = dialogView.findViewById<SwitchCompat>(R.id.switchInternalFillEnabled)
        val internalHeightSwitch = dialogView.findViewById<SwitchCompat>(R.id.switchInternalFillHeight)
        val internalWidthSwitch = dialogView.findViewById<SwitchCompat>(R.id.switchInternalFillWidth)
        val externalEnabledSwitch = dialogView.findViewById<SwitchCompat>(R.id.switchExternalFillEnabled)
        val externalHeightSwitch = dialogView.findViewById<SwitchCompat>(R.id.switchExternalFillHeight)
        val externalWidthSwitch = dialogView.findViewById<SwitchCompat>(R.id.switchExternalFillWidth)

        description.text = getString(R.string.dual_screen_fill_area_description)
        internalHeightSwitch.isChecked = initialInternalFillHeight
        internalWidthSwitch.isChecked = initialInternalFillWidth
        externalHeightSwitch.isChecked = initialExternalFillHeight
        externalWidthSwitch.isChecked = initialExternalFillWidth
        internalEnabledSwitch.isChecked = initialInternalFillHeight || initialInternalFillWidth
        externalEnabledSwitch.isChecked = initialExternalFillHeight || initialExternalFillWidth

        fun updateInternalSection(enabled: Boolean, mutateValues: Boolean) {
            val childEnabled = fillOptionsEnabled && enabled
            internalHeightSwitch.isEnabled = childEnabled
            internalWidthSwitch.isEnabled = childEnabled
            if (!mutateValues) {
                return
            }
            if (!enabled) {
                internalHeightSwitch.isChecked = false
                internalWidthSwitch.isChecked = false
            } else if (!internalHeightSwitch.isChecked && !internalWidthSwitch.isChecked) {
                internalHeightSwitch.isChecked = true
            }
        }

        fun updateExternalSection(enabled: Boolean, mutateValues: Boolean) {
            val childEnabled = fillOptionsEnabled && enabled
            externalHeightSwitch.isEnabled = childEnabled
            externalWidthSwitch.isEnabled = childEnabled
            if (!mutateValues) {
                return
            }
            if (!enabled) {
                externalHeightSwitch.isChecked = false
                externalWidthSwitch.isChecked = false
            } else if (!externalHeightSwitch.isChecked && !externalWidthSwitch.isChecked) {
                externalHeightSwitch.isChecked = true
            }
        }

        internalEnabledSwitch.isEnabled = fillOptionsEnabled
        externalEnabledSwitch.isEnabled = fillOptionsEnabled
        updateInternalSection(internalEnabledSwitch.isChecked, mutateValues = false)
        updateExternalSection(externalEnabledSwitch.isChecked, mutateValues = false)
        internalEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateInternalSection(isChecked, mutateValues = true)
        }
        externalEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateExternalSection(isChecked, mutateValues = true)
        }
        disabledText.isVisible = !fillOptionsEnabled

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dual_screen_fill_area_title)
            .setView(dialogView)
            .setPositiveButton(R.string.ok) { _, _ ->
                val internalEnabled = fillOptionsEnabled && internalEnabledSwitch.isChecked
                val externalEnabled = fillOptionsEnabled && externalEnabledSwitch.isChecked
                onValuesConfirmed(
                    if (internalEnabled) internalHeightSwitch.isChecked else false,
                    if (internalEnabled) internalWidthSwitch.isChecked else false,
                    if (externalEnabled) externalHeightSwitch.isChecked else false,
                    if (externalEnabled) externalWidthSwitch.isChecked else false,
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showVerticalAlignmentOptionsDialog(
        preset: DualScreenPreset,
        initialInternalAlignment: ScreenAlignment?,
        initialExternalAlignment: ScreenAlignment?,
        onValuesConfirmed: (ScreenAlignment?, ScreenAlignment?) -> Unit,
    ) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_dual_screen_vertical_alignment, null)
        val description = dialogView.findViewById<TextView>(R.id.textVerticalAlignmentDescription)
        val defaults = dialogView.findViewById<TextView>(R.id.textVerticalAlignmentDefaults)
        description.text = getString(R.string.dual_screen_vertical_alignment_description)
        defaults.text = getString(
            R.string.dual_screen_vertical_alignment_default_hint,
            getAlignmentDisplayName(preset.defaultInternalAlignment()),
            getAlignmentDisplayName(preset.defaultExternalAlignment()),
        )

        val internalToggle = dialogView.findViewById<SwitchCompat>(R.id.switchInternalAlignmentOverride)
        val externalToggle = dialogView.findViewById<SwitchCompat>(R.id.switchExternalAlignmentOverride)
        val internalRadioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroupInternalAlignment)
        val externalRadioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroupExternalAlignment)
        val internalRadios = mapOf(
            ScreenAlignment.TOP to dialogView.findViewById<RadioButton>(R.id.radioInternalAlignmentTop),
            ScreenAlignment.CENTER to dialogView.findViewById<RadioButton>(R.id.radioInternalAlignmentCenter),
            ScreenAlignment.BOTTOM to dialogView.findViewById<RadioButton>(R.id.radioInternalAlignmentBottom),
        )
        val externalRadios = mapOf(
            ScreenAlignment.TOP to dialogView.findViewById<RadioButton>(R.id.radioExternalAlignmentTop),
            ScreenAlignment.CENTER to dialogView.findViewById<RadioButton>(R.id.radioExternalAlignmentCenter),
            ScreenAlignment.BOTTOM to dialogView.findViewById<RadioButton>(R.id.radioExternalAlignmentBottom),
        )

        var currentInternalSelection = initialInternalAlignment ?: preset.defaultInternalAlignment()
        var currentExternalSelection = initialExternalAlignment ?: preset.defaultExternalAlignment()
        var pendingInternalAlignment = initialInternalAlignment
        var pendingExternalAlignment = initialExternalAlignment
        var updatingInternalRadios = false
        var updatingExternalRadios = false

        fun setRadiosEnabled(radios: Collection<RadioButton>, enabled: Boolean) {
            radios.forEach { it.isEnabled = enabled }
        }

        fun applyInternalSelection() {
            updatingInternalRadios = true
            internalRadioGroup.check(internalRadios.getValue(currentInternalSelection).id)
            updatingInternalRadios = false
        }

        fun applyExternalSelection() {
            updatingExternalRadios = true
            externalRadioGroup.check(externalRadios.getValue(currentExternalSelection).id)
            updatingExternalRadios = false
        }

        internalToggle.isChecked = pendingInternalAlignment != null
        externalToggle.isChecked = pendingExternalAlignment != null
        applyInternalSelection()
        applyExternalSelection()
        setRadiosEnabled(internalRadios.values, internalToggle.isChecked)
        setRadiosEnabled(externalRadios.values, externalToggle.isChecked)

        internalToggle.setOnCheckedChangeListener { _, isChecked ->
            setRadiosEnabled(internalRadios.values, isChecked)
            pendingInternalAlignment = if (isChecked) currentInternalSelection else null
        }
        externalToggle.setOnCheckedChangeListener { _, isChecked ->
            setRadiosEnabled(externalRadios.values, isChecked)
            pendingExternalAlignment = if (isChecked) currentExternalSelection else null
        }

        internalRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (updatingInternalRadios) {
                return@setOnCheckedChangeListener
            }
            currentInternalSelection = when (checkedId) {
                R.id.radioInternalAlignmentTop -> ScreenAlignment.TOP
                R.id.radioInternalAlignmentCenter -> ScreenAlignment.CENTER
                R.id.radioInternalAlignmentBottom -> ScreenAlignment.BOTTOM
                else -> ScreenAlignment.TOP
            }
            if (internalToggle.isChecked) {
                pendingInternalAlignment = currentInternalSelection
            }
        }

        externalRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (updatingExternalRadios) {
                return@setOnCheckedChangeListener
            }
            currentExternalSelection = when (checkedId) {
                R.id.radioExternalAlignmentTop -> ScreenAlignment.TOP
                R.id.radioExternalAlignmentCenter -> ScreenAlignment.CENTER
                R.id.radioExternalAlignmentBottom -> ScreenAlignment.BOTTOM
                else -> ScreenAlignment.TOP
            }
            if (externalToggle.isChecked) {
                pendingExternalAlignment = currentExternalSelection
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dual_screen_vertical_alignment_title)
            .setView(dialogView)
            .setPositiveButton(R.string.ok) { _, _ ->
                onValuesConfirmed(pendingInternalAlignment, pendingExternalAlignment)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun getVerticalAlignmentSummary(
        preset: DualScreenPreset,
        internalOverride: ScreenAlignment?,
        externalOverride: ScreenAlignment?,
    ): String {
        val internalDefault = preset.defaultInternalAlignment()
        val externalDefault = preset.defaultExternalAlignment()
        val internalLabel = formatAlignmentLabel(internalOverride, internalDefault)
        val externalLabel = formatAlignmentLabel(externalOverride, externalDefault)
        return getString(R.string.dual_screen_vertical_alignment_summary, internalLabel, externalLabel)
    }

    private fun formatAlignmentLabel(override: ScreenAlignment?, defaultAlignment: ScreenAlignment): String {
        return if (override == null) {
            getString(
                R.string.dual_screen_vertical_alignment_preset_value,
                getAlignmentDisplayName(defaultAlignment),
            )
        } else {
            getAlignmentDisplayName(override)
        }
    }

    private fun getAlignmentDisplayName(alignment: ScreenAlignment): String {
        return when (alignment) {
            ScreenAlignment.TOP -> getString(R.string.dual_screen_vertical_alignment_option_top)
            ScreenAlignment.CENTER -> getString(R.string.dual_screen_vertical_alignment_option_center)
            ScreenAlignment.BOTTOM -> getString(R.string.dual_screen_vertical_alignment_option_bottom)
        }
    }

    override fun getTitle(): String {
        return getString(R.string.category_video)
    }
}
