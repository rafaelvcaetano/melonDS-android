package me.magnum.melonds.impl

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import me.magnum.melonds.model.ControllerConfiguration
import me.magnum.melonds.model.VideoFiltering
import me.magnum.melonds.repositories.SettingsRepository
import me.magnum.melonds.ui.Theme
import java.io.*
import java.util.*

class SharedPreferencesSettingsRepository(private val context: Context, private val preferences: SharedPreferences, private val gson: Gson) : SettingsRepository, OnSharedPreferenceChangeListener {
    companion object {
        private const val TAG = "SPSettingsRepository"
        private const val CONTROLLER_CONFIG_FILE = "controller_config.json"

        /**
         * Retrieves a single directory from the given directory preference value. If multiple
         * directories are stored in the preference, only the first value is returned. If no directory
         * is stored in the preference value, `null` is returned. Directories are assumed to be
         * separated by a column (:).
         *
         * @param preferenceValue The directory preference value
         * @return The first directory found in the preference or `null` if there is none
         */
        private fun getSingleDirectoryFromPreference(preferenceValue: String?): String? {
            val parts = getMultipleDirectoryFromPreference(preferenceValue)
            return if (parts.isNotEmpty()) parts[0] else null
        }

        /**
         * Retrieves all directory from the given directory preference value. If no directory is stored
         * in the preference value, an empty array is returned. Directories are assumed to be separated
         * by a column (:).
         *
         * @param preferenceValue The directory preference value
         * @return The directories found in the preference
         */
        private fun getMultipleDirectoryFromPreference(preferenceValue: String?): Array<String> {
            return preferenceValue?.split(":")?.toTypedArray() ?: emptyArray()
        }
    }

    private var controllerConfiguration: ControllerConfiguration? = null
    private val preferenceObservers: HashMap<String, PublishSubject<Any>> = HashMap()

    init {
        preferences.registerOnSharedPreferenceChangeListener(this)
        setDefaultThemeIfRequired()
    }

    private fun setDefaultThemeIfRequired() {
        if (preferences.getString("theme", null) != null) return
        val defaultTheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "system" else "light"
        preferences.edit().putString("theme", defaultTheme).apply()
    }

    override fun getTheme(): Theme {
        val themePreference = preferences.getString("theme", "light")!!
        return Theme.valueOf(themePreference.toUpperCase(Locale.ROOT))
    }

    override fun getRomSearchDirectories(): Array<String> {
        val dirPreference = preferences.getString("rom_search_dirs", null)
        var dirs: Array<String> = getMultipleDirectoryFromPreference(dirPreference)

        if (dirs.isEmpty())
            dirs = arrayOf("/sdcard")

        return dirs
    }

    override fun getBiosDirectory(): String? {
        val dirPreference = preferences.getString("bios_dir", null)
        return getSingleDirectoryFromPreference(dirPreference)
    }

    override fun showBootScreen(): Boolean {
        return preferences.getBoolean("show_bios", false)
    }

    override fun getVideoFiltering(): VideoFiltering {
        val filteringPreference = preferences.getString("video_filtering", "linear")!!
        return VideoFiltering.valueOf(filteringPreference.toUpperCase(Locale.ROOT))
    }

    override fun saveNextToRomFile(): Boolean {
        return preferences.getBoolean("use_rom_dir", true)
    }

    override fun getSaveFileDirectory(): String? {
        val dirPreference = preferences.getString("sram_dir", null)
        return getSingleDirectoryFromPreference(dirPreference)
    }

    override fun getControllerConfiguration(): ControllerConfiguration {
        if (controllerConfiguration == null) {
            try {
                val configFile = File(context.filesDir, CONTROLLER_CONFIG_FILE)
                FileReader(configFile).use {
                    val loadedConfiguration = gson.fromJson(it, ControllerConfiguration::class.java)
                    // Create new instance to validate loaded configuration
                    controllerConfiguration = ControllerConfiguration(loadedConfiguration?.inputMapper ?: emptyList())
                }
            } catch (e: IOException) {
                Log.w(TAG, "Failed to load controller configuration", e)
                controllerConfiguration = ControllerConfiguration.empty()
            }
        }
        return controllerConfiguration!!
    }

    override fun showSoftInput(): Boolean {
        return preferences.getBoolean("input_show_soft", true)
    }

    override fun getSoftInputOpacity(): Int {
        return preferences.getInt("input_opacity", 50)
    }

    override fun observeRomSearchDirectories(): Observable<Array<String>> {
        return getOrCreatePreferenceObservable("rom_search_dirs") {
            getRomSearchDirectories()
        }
    }

    override fun setControllerConfiguration(controllerConfiguration: ControllerConfiguration) {
        this.controllerConfiguration = controllerConfiguration

        try {
            val configFile = File(context.filesDir, CONTROLLER_CONFIG_FILE)
            OutputStreamWriter(FileOutputStream(configFile)).use {
                val configJson = gson.toJson(controllerConfiguration)
                it.write(configJson)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Failed to save controller configuration", e)
        }
    }

    override fun observeTheme(): Observable<Theme> {
        return getOrCreatePreferenceObservable("theme") {
            getTheme()
        }
    }

    private fun <T> getOrCreatePreferenceObservable(preference: String, mapper: (Any) -> T): Observable<T> {
        var preferenceSubject = preferenceObservers[preference]
        if (preferenceSubject == null) {
            preferenceSubject = PublishSubject.create()
            preferenceObservers[preference] = preferenceSubject
        }
        return preferenceSubject.map(mapper)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        val subject = preferenceObservers[key]
        subject?.onNext(Any())
    }
}