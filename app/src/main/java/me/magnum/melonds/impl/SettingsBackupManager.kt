package me.magnum.melonds.impl

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsBackupManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: SharedPreferences,
) {
    companion object {
        private const val SETTINGS_FILE = "settings.json"
        private const val CONTROLLER_FILE = "controller_config.json"
        private const val LAYOUTS_FILE = "layouts.json"
        private const val INTERNAL_LAYOUT_FILE = "internal_layout.json"
        private const val EXTERNAL_LAYOUT_FILE = "external_layout.json"
        private const val ROM_DATA_FILE = "rom_data.json"
        private val EXCLUDED_PREF_KEYS = setOf(
            "ra_username",
            "ra_token",
            "sram_dir",
            "rom_search_dirs",
            "bios_dir",
            "dsi_bios_dir"
        )
        private val LONG_PREF_KEYS = setOf(
            "ra_hash_library_last_updated",
            "github_updates_nightly_next_check_date",
            "github_updates_nightly_last_release_date",
            "github_updates_last_check",
            "last_version",
        )
    }

    fun backup(treeUri: Uri) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return

        val settingsDoc = root.findFile(SETTINGS_FILE)
            ?: root.createFile("application/json", SETTINGS_FILE)
            ?: return
        context.contentResolver.openOutputStream(settingsDoc.uri)?.use { outStream ->
            val json = JSONObject()
            for ((key, value) in preferences.all) {
                if (key in EXCLUDED_PREF_KEYS) continue
                when (value) {
                    is Boolean, is Int, is Long, is Float, is String -> json.put(key, value)
                    is Set<*> -> {
                        val array = JSONArray()
                        value.forEach { array.put(it) }
                        json.put(key, array)
                    }
                }
            }
            outStream.writer().use { it.write(json.toString()) }
        }

        val controllerFile = File(context.filesDir, CONTROLLER_FILE)
        if (controllerFile.exists()) {
            val controllerDoc = root.findFile(CONTROLLER_FILE)
                ?: root.createFile("application/json", CONTROLLER_FILE)
                ?: return
            context.contentResolver.openOutputStream(controllerDoc.uri)?.use { out ->
                controllerFile.inputStream().use { it.copyTo(out) }
            }
        }

        val layoutsFile = File(context.filesDir, LAYOUTS_FILE)
        if (layoutsFile.exists()) {
            val layoutsDoc = root.findFile(LAYOUTS_FILE)
                ?: root.createFile("application/json", LAYOUTS_FILE)
                ?: return
            context.contentResolver.openOutputStream(layoutsDoc.uri)?.use { out ->
                layoutsFile.inputStream().use { it.copyTo(out) }
            }
        }

        val romDataFile = File(context.filesDir, ROM_DATA_FILE)
        if (romDataFile.exists()) {
            val romDataDoc = root.findFile(ROM_DATA_FILE)
                ?: root.createFile("application/json", ROM_DATA_FILE)
                ?: return
            context.contentResolver.openOutputStream(romDataDoc.uri)?.use { out ->
                romDataFile.inputStream().use { it.copyTo(out) }
            }
        }
    }

    fun restore(treeUri: Uri) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return

        val settingsDoc = root.findFile(SETTINGS_FILE)
        settingsDoc?.uri?.let { uri ->
            context.contentResolver.openInputStream(uri)?.use { input ->
                val text = input.reader().readText()
                val json = JSONObject(text)
                val editor = preferences.edit()
                for (key in json.keys()) {
                    if (key in EXCLUDED_PREF_KEYS) continue
                    val value = json.get(key)
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> {
                            if (key in LONG_PREF_KEYS) {
                                editor.putLong(key, value.toLong())
                            } else {
                                editor.putInt(key, value)
                            }
                        }
                        is Long -> editor.putLong(key, value)
                        is Double -> editor.putFloat(key, value.toFloat())
                        is String -> editor.putString(key, value)
                        is JSONArray -> {
                            val set = mutableSetOf<String>()
                            for (i in 0 until value.length()) {
                                set.add(value.getString(i))
                            }
                            editor.putStringSet(key, set)
                        }
                        is Number -> {
                            val current = preferences.all[key]
                            when {
                                current is Long || key in LONG_PREF_KEYS -> editor.putLong(key, value.toLong())
                                current is Int -> editor.putInt(key, value.toInt())
                                current is Float -> editor.putFloat(key, value.toFloat())
                                value is Double -> editor.putFloat(key, value.toFloat())
                                else -> editor.putLong(key, value.toLong())
                            }
                        }
                    }
                }
                editor.apply()
            }
        }

        val controllerDoc = root.findFile(CONTROLLER_FILE)
        controllerDoc?.uri?.let { uri ->
            val dest = File(context.filesDir, CONTROLLER_FILE)
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        val layoutsDoc = root.findFile(LAYOUTS_FILE)
        layoutsDoc?.uri?.let { uri ->
            val dest = File(context.filesDir, LAYOUTS_FILE)
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        val romDataDoc = root.findFile(ROM_DATA_FILE)
        romDataDoc?.uri?.let { uri ->
            val dest = File(context.filesDir, ROM_DATA_FILE)
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    fun backupInternalLayout(treeUri: Uri) {
        backupFilteredLayout(treeUri, INTERNAL_LAYOUT_FILE, "INTERNAL")
    }

    fun backupExternalLayout(treeUri: Uri) {
        backupFilteredLayout(treeUri, EXTERNAL_LAYOUT_FILE, "EXTERNAL")
    }

    fun restoreInternalLayout(treeUri: Uri) {
        restoreFilteredLayout(treeUri, INTERNAL_LAYOUT_FILE, "INTERNAL")
    }

    fun restoreExternalLayout(treeUri: Uri) {
        restoreFilteredLayout(treeUri, EXTERNAL_LAYOUT_FILE, "EXTERNAL")
    }

    private fun backupFilteredLayout(treeUri: Uri, fileName: String, target: String) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return

        val layoutsSrc = File(context.filesDir, LAYOUTS_FILE)
        if (!layoutsSrc.exists()) return

        val layoutsText = runCatching { layoutsSrc.readText() }.getOrNull() ?: return
        val allLayouts = runCatching { JSONArray(layoutsText) }.getOrNull() ?: return
        val filtered = JSONArray()
        for (i in 0 until allLayouts.length()) {
            val obj = allLayouts.optJSONObject(i) ?: continue
            if (obj.optString("target") == target) {
                filtered.put(obj)
            }
        }

        val dest = root.findFile(fileName) ?: root.createFile("application/json", fileName) ?: return
        context.contentResolver.openOutputStream(dest.uri)?.use { out ->
            out.writer().use { it.write(filtered.toString()) }
        }
    }

    private fun restoreFilteredLayout(treeUri: Uri, fileName: String, target: String) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return
        val src = root.findFile(fileName) ?: return

        val backupArray = context.contentResolver.openInputStream(src.uri)?.use { input ->
            runCatching { JSONArray(input.reader().readText()) }.getOrNull()
        } ?: return

        val layoutsFile = File(context.filesDir, LAYOUTS_FILE)
        val existingArray = if (layoutsFile.exists()) {
            runCatching { JSONArray(layoutsFile.readText()) }.getOrElse { JSONArray() }
        } else {
            JSONArray()
        }

        val merged = JSONArray()
        for (i in 0 until existingArray.length()) {
            val obj = existingArray.optJSONObject(i) ?: continue
            if (obj.optString("target") != target) {
                merged.put(obj)
            }
        }
        for (i in 0 until backupArray.length()) {
            merged.put(backupArray.getJSONObject(i))
        }
        layoutsFile.outputStream().use { out ->
            out.writer().use { it.write(merged.toString()) }
        }
    }
}

