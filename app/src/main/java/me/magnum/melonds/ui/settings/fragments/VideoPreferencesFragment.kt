package me.magnum.melonds.ui.settings.fragments

import android.app.ActivityManager
import android.os.Bundle
import androidx.core.content.getSystemService
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.common.DirectoryAccessValidator
import me.magnum.melonds.common.UriPermissionManager
import me.magnum.melonds.domain.model.VideoRenderer
import me.magnum.melonds.domain.model.camera.DSiCameraSourceType
import me.magnum.melonds.ui.settings.PreferenceFragmentHelper
import me.magnum.melonds.ui.settings.PreferenceFragmentTitleProvider
import me.magnum.melonds.ui.settings.preferences.StoragePickerPreference
import me.magnum.melonds.utils.enumValueOfIgnoreCase
import javax.inject.Inject

@AndroidEntryPoint
class VideoPreferencesFragment : PreferenceFragmentCompat(), PreferenceFragmentTitleProvider {

    private companion object {
        const val GLES_3_2 = 0x30002
    }

    private val helper by lazy { PreferenceFragmentHelper(this, uriPermissionManager, directoryAccessValidator) }
    @Inject lateinit var uriPermissionManager: UriPermissionManager
    @Inject lateinit var directoryAccessValidator: DirectoryAccessValidator

    private val softwareRendererPreferences = mutableListOf<Preference>()
    private val openGlRendererPreferences = mutableListOf<Preference>()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_video, rootKey)

        softwareRendererPreferences.apply {
            add(findPreference("video_filtering")!!)
            add(findPreference("enable_threaded_rendering")!!)
        }

        openGlRendererPreferences.apply {
            add(findPreference("video_internal_resolution")!!)
        }

        val rendererPreference = findPreference<ListPreference>("video_renderer")!!
        val dsiCameraSourcePreference = findPreference<ListPreference>("dsi_camera_source")!!
        val dsiCameraImagePreference = findPreference<StoragePickerPreference>("dsi_camera_static_image")!!

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

        helper.setupStoragePickerPreference(dsiCameraImagePreference)

        onRendererPreferenceChanged(rendererPreference.value)
        updateDsiCameraImagePreference(dsiCameraImagePreference, dsiCameraSourcePreference.value)
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

    override fun getTitle(): String {
        return getString(R.string.category_video)
    }
}