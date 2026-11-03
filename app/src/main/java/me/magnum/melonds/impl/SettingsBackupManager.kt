package me.magnum.melonds.impl

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.AtomicFile
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.magnum.melonds.domain.model.layout.LayoutConfiguration
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.impl.dtos.input.ControllerConfigurationDto
import me.magnum.melonds.impl.dtos.input.InputConfigDto
import me.magnum.melonds.impl.dtos.layout.LayoutConfigurationDto
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

internal object SettingsRestoreRecovery {
    private const val LEGACY_PREVIOUS_SUFFIX = ".restore.previous"
    private const val LEGACY_TEMP_SUFFIX = ".restore.tmp"

    fun recoverAll(filesDir: File) {
        recoverFile(File(filesDir, "controller_config.json"))
        recoverFile(File(filesDir, "layouts.json"))
    }

    fun recoverFile(destination: File) {
        val previous = File(destination.parentFile, destination.name + LEGACY_PREVIOUS_SUFFIX)
        if (!destination.exists() && previous.isFile) previous.renameTo(destination)
        if (destination.exists()) previous.delete()
        File(destination.parentFile, destination.name + LEGACY_TEMP_SUFFIX).delete()

        // openRead rolls an interrupted AtomicFile write back from its .bak file.
        runCatching { AtomicFile(destination).openRead().close() }
    }
}

@Singleton
class SettingsBackupManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: SharedPreferences,
    private val settingsRepository: SettingsRepository,
    private val gson: Gson,
    private val json: Json,
) {
    companion object {
        private const val SETTINGS_FILE = "settings.json"
        private const val CONTROLLER_FILE = "controller_config.json"
        private const val LAYOUTS_FILE = "layouts.json"
        private const val INTERNAL_LAYOUT_FILE = "internal_layout.json"
        private const val EXTERNAL_LAYOUT_FILE = "external_layout.json"
        private const val ROM_DATA_FILE = "rom_data.json"
        private const val MAX_SETTINGS_BYTES = 1024 * 1024
        private const val MAX_CONFIG_BYTES = 8 * 1024 * 1024
        private const val MAX_LAYOUTS = 256
        private const val MAX_VARIANTS_PER_LAYOUT = 64
        private const val MAX_FOLDS_PER_VARIANT = 32
        private const val MAX_COMPONENTS_PER_SCREEN = 512
        private const val MAX_LAYOUT_COORDINATE = 1_000_000
        private val EXCLUDED_PREF_KEYS = setOf(
            "ra_username",
            "ra_token",
            "sram_dir",
            "rom_search_dirs",
            "bios_dir",
            "dsi_bios_dir",
            "dsi_camera_static_image",
            "last_version",
            "ra_hash_library_last_updated",
            "github_updates_nightly_next_check_date",
            "github_updates_nightly_last_release_date",
            "github_updates_last_check",
            "github_updates_skip_version",
        )
        private val LONG_PREF_KEYS = emptySet<String>()
        private val BOOLEAN_PREF_KEYS = setOf(
            "cheats_enabled", "custom_randomize_mac_address", "enable_jit", "enable_rewind",
            "enable_sustained_performance", "enable_threaded_rendering", "front_rendering",
            "input_show_soft", "input_touch_haptic_feedback_enabled", "internal_randomize_mac_address",
            "github_check_for_updates",
            "mute_fast_forward_audio", "ra_active_challenge_indicators", "ra_hardcore_enabled",
            "ra_leaderboard_indicators", "ra_progress_indicators", "ra_rich_presence", "rtc_sync_to_host",
            "show_bios", "sound_enabled", "use_custom_bios", "use_rom_dir",
        )
        private val INT_PREF_KEYS = setOf(
            "firmware_settings_colour", "input_opacity", "input_touch_haptic_feedback_strength",
            "rewind_period", "rewind_window", "rom_cache_max_size", "volume",
        )
        private val STRING_PREF_KEYS = setOf(
            "audio_bitrate", "audio_interpolation", "audio_latency", "console_type", "dsi_camera_source",
            "fast_forward_speed_multiplier", "firmware_settings_birthday", "firmware_settings_language",
            "firmware_settings_message", "firmware_settings_nickname", "fps_counter_position",
            "input_layout_id", "internal_mac_address", "mic_source", "rewind_window_position",
            "rom_icon_filtering", "rom_sorting_mode", "rom_sorting_order", "save_state_location",
            "soft_input_behaviour", "theme", "video_filtering", "video_internal_resolution", "video_renderer",
        )
        private val STRING_SET_PREF_KEYS = setOf(
            "bios_dir", "dsi_bios_dir", "dsi_camera_static_image", "rom_search_dirs", "sram_dir",
        )
        private val ENUM_STRING_VALUES = mapOf(
            "theme" to setOf("light", "dark", "system"),
            "rewind_window_position" to setOf("top", "bottom"),
            "audio_bitrate" to setOf("auto", "bit10", "bit16"),
            "audio_interpolation" to setOf("none", "linear", "cosine", "cubic"),
            "audio_latency" to setOf("low", "medium", "high"),
            "console_type" to setOf("ds", "dsi"),
            "dsi_camera_source" to setOf("black_screen", "physical_cameras", "static_image"),
            "fps_counter_position" to setOf("hidden", "top_left", "top_center", "top_right", "bottom_left", "bottom_center", "bottom_right"),
            "mic_source" to setOf("none", "blow", "device"),
            "rom_icon_filtering" to setOf("none", "linear"),
            "rom_sorting_mode" to setOf("alphabetically", "recently_played"),
            "rom_sorting_order" to setOf("ascending", "descending"),
            "save_state_location" to setOf("save_dir", "rom_dir", "internal_dir"),
            "soft_input_behaviour" to setOf("always_visible", "hide_system_buttons_when_controller_connected", "hide_mapped_buttons_when_controller_connected", "always_invisible"),
            "video_filtering" to setOf("none", "linear", "xbr2", "hq2x", "hq4x", "quilez", "lcd", "scanlines"),
            "video_renderer" to setOf("software", "opengl", "compute"),
            "firmware_settings_language" to setOf("0", "1", "2", "3", "4", "5"),
        )
        private val INT_PREF_RANGES = mapOf(
            "firmware_settings_colour" to 0..15,
            "input_opacity" to 0..100,
            "input_touch_haptic_feedback_strength" to 1..100,
            "rewind_period" to 1..60,
            "rewind_window" to 1..30,
            "rom_cache_max_size" to 0..6,
            "volume" to 0..256,
        )
    }

    private enum class PreferenceKind { BOOLEAN, INT, LONG, FLOAT, STRING, STRING_SET }

    fun backup(treeUri: Uri) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return
        backupToDocument(root)
    }

    // Extracted so tests can exercise the file-writing logic against a plain, file-backed
    // DocumentFile (DocumentFile.fromFile) instead of needing a real SAF tree Uri.
    internal fun backupToDocument(root: DocumentFile) {
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

        // rom_data.json is a derived authorization-bearing cache. Rebuild it
        // from currently granted ROM trees instead of exporting/restoring paths.
    }

    fun restore(treeUri: Uri) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return
        restoreFromDocument(root)
    }

    // See backupToDocument: extracted for testing against a file-backed DocumentFile.
    internal fun restoreFromDocument(root: DocumentFile) {
        SettingsRestoreRecovery.recoverAll(context.filesDir)
        val settingsDoc = root.findFile(SETTINGS_FILE)
        settingsDoc?.uri?.let { uri ->
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = readBounded(input, MAX_SETTINGS_BYTES) ?: return@use
                val text = String(bytes, StandardCharsets.UTF_8)
                val json = JSONObject(text)
                val editor = preferences.edit()
                for (key in json.keys()) {
                    if (key in EXCLUDED_PREF_KEYS) continue
                    val value = json.get(key)
                    when (expectedPreferenceKind(key, preferences.all[key])) {
                        PreferenceKind.BOOLEAN -> if (value is Boolean) editor.putBoolean(key, value)
                        PreferenceKind.INT -> if (value is Number && value.toLong() in Int.MIN_VALUE..Int.MAX_VALUE) {
                            val intValue = value.toInt()
                            if (INT_PREF_RANGES[key]?.contains(intValue) != false) editor.putInt(key, intValue)
                        }
                        PreferenceKind.LONG -> if (value is Number) editor.putLong(key, value.toLong())
                        PreferenceKind.FLOAT -> if (value is Number && value.toDouble().isFinite()) {
                            editor.putFloat(key, value.toFloat())
                        }
                        PreferenceKind.STRING -> if (value is String && validStringPreference(key, value)) {
                            editor.putString(key, value)
                        }
                        PreferenceKind.STRING_SET -> if (value is JSONArray) {
                            val set = mutableSetOf<String>()
                            var valid = true
                            for (i in 0 until value.length()) {
                                val member = value.get(i)
                                if (member !is String) {
                                    valid = false
                                    break
                                }
                                set.add(member)
                            }
                            if (valid && set.size <= 256 && set.all { it.length <= 4096 }) editor.putStringSet(key, set)
                        }
                        null -> Unit
                    }
                }
                editor.apply()
            }
        }

        val controllerDoc = root.findFile(CONTROLLER_FILE)
        controllerDoc?.uri?.let { uri ->
            val dest = File(context.filesDir, CONTROLLER_FILE)
            val restored = context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = readBounded(input, MAX_CONFIG_BYTES) ?: return@use false
                val canonical = canonicalControllerConfiguration(bytes) ?: return@use false
                copyBoundedAtomically(canonical.inputStream(), dest, MAX_CONFIG_BYTES)
            } == true
            // The repository caches this file's content in memory; without this it keeps serving the
            // pre-restore configuration until the process restarts.
            if (restored) settingsRepository.reloadControllerConfiguration()
        }

        val layoutsDoc = root.findFile(LAYOUTS_FILE)
        layoutsDoc?.uri?.let { uri ->
            val dest = File(context.filesDir, LAYOUTS_FILE)
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = readBounded(input, MAX_CONFIG_BYTES) ?: return@use
                val layouts = runCatching { JSONArray(String(bytes, StandardCharsets.UTF_8)) }.getOrNull() ?: return@use
                val canonical = canonicalLayoutsArray(layouts) ?: return@use
                copyBoundedAtomically(canonical.toString().byteInputStream(), dest, MAX_CONFIG_BYTES)
            }
        }
    }

    private fun expectedPreferenceKind(key: String, current: Any?): PreferenceKind? {
        return when {
            key in BOOLEAN_PREF_KEYS -> PreferenceKind.BOOLEAN
            key in INT_PREF_KEYS -> PreferenceKind.INT
            key in LONG_PREF_KEYS -> PreferenceKind.LONG
            key in STRING_PREF_KEYS || key.startsWith("wfc_slot_name_") -> PreferenceKind.STRING
            key in STRING_SET_PREF_KEYS -> PreferenceKind.STRING_SET
            current is Boolean -> PreferenceKind.BOOLEAN
            current is Int -> PreferenceKind.INT
            current is Long -> PreferenceKind.LONG
            current is Float -> PreferenceKind.FLOAT
            current is String -> PreferenceKind.STRING
            current is Set<*> && current.all { it is String } -> PreferenceKind.STRING_SET
            else -> null
        }
    }

    private fun validStringPreference(key: String, value: String): Boolean {
        if (value.length > 4096) return false
        ENUM_STRING_VALUES[key]?.let { return value.lowercase() in it }
        return when (key) {
            "fast_forward_speed_multiplier" -> value.toFloatOrNull()?.let { it.isFinite() && (it == -1f || it in 1f..16f) } == true
            "video_internal_resolution" -> value.toIntOrNull()?.let { it in 1..8 } == true
            "input_layout_id" -> runCatching { UUID.fromString(value) }.isSuccess
            "internal_mac_address" -> value.matches(Regex("(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}"))
            "firmware_settings_birthday" -> value.split('/').let { parts ->
                parts.size == 2 &&
                    parts[0].toIntOrNull()?.let { it in 1..31 } == true &&
                    parts[1].toIntOrNull()?.let { it in 1..12 } == true
            }
            else -> true
        }
    }

    private fun readBounded(input: InputStream, limit: Int): ByteArray? {
        val output = java.io.ByteArrayOutputStream(minOf(limit, 8192))
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            if (count > limit - total) return null
            output.write(buffer, 0, count)
            total += count
        }
        return output.toByteArray()
    }

    private fun copyBoundedAtomically(input: InputStream, destination: File, limit: Int): Boolean {
        SettingsRestoreRecovery.recoverFile(destination)
        val atomicFile = AtomicFile(destination)
        var output: java.io.FileOutputStream? = null
        var total = 0
        return try {
            val stream = atomicFile.startWrite()
            output = stream
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                if (count > limit - total) {
                    atomicFile.failWrite(output)
                    output = null
                    return false
                }
                stream.write(buffer, 0, count)
                total += count
            }
            atomicFile.finishWrite(stream)
            output = null
            true
        } catch (error: Exception) {
            output?.let(atomicFile::failWrite)
            throw error
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
        backupFilteredLayoutToDocument(root, fileName, target)
    }

    internal fun backupFilteredLayoutToDocument(root: DocumentFile, fileName: String, target: String) {
        SettingsRestoreRecovery.recoverFile(File(context.filesDir, LAYOUTS_FILE))
        val layoutsSrc = File(context.filesDir, LAYOUTS_FILE)
        if (!layoutsSrc.exists()) return

        val layoutsText = layoutsSrc.inputStream().use { input ->
            readBounded(input, MAX_CONFIG_BYTES)?.toString(StandardCharsets.UTF_8)
        } ?: return
        val allLayouts = runCatching { JSONArray(layoutsText) }.getOrNull() ?: return
        if (allLayouts.length() > MAX_LAYOUTS) return
        val filtered = JSONArray()
        for (i in 0 until allLayouts.length()) {
            val obj = allLayouts.optJSONObject(i) ?: continue
            val canonical = canonicalLayout(obj) ?: continue
            filterLayoutVariants(canonical, target, keepMatches = true, preserveMalformed = false)?.let(filtered::put)
        }

        val dest = root.findFile(fileName) ?: root.createFile("application/json", fileName) ?: return
        context.contentResolver.openOutputStream(dest.uri)?.use { out ->
            out.writer().use { it.write(filtered.toString()) }
        }
    }

    private fun restoreFilteredLayout(treeUri: Uri, fileName: String, target: String) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return
        restoreFilteredLayoutFromDocument(root, fileName, target)
    }

    internal fun restoreFilteredLayoutFromDocument(root: DocumentFile, fileName: String, target: String) {
        val layoutsFile = File(context.filesDir, LAYOUTS_FILE)
        SettingsRestoreRecovery.recoverFile(layoutsFile)
        val src = root.findFile(fileName) ?: return

        val backupArray = context.contentResolver.openInputStream(src.uri)?.use { input ->
            val bytes = readBounded(input, MAX_CONFIG_BYTES) ?: return@use null
            runCatching { JSONArray(String(bytes, StandardCharsets.UTF_8)) }.getOrNull()
        } ?: return
        if (backupArray.length() > MAX_LAYOUTS) return

        val existingArray = if (layoutsFile.exists()) {
            val bytes = layoutsFile.inputStream().use { readBounded(it, MAX_CONFIG_BYTES) } ?: return
            runCatching { JSONArray(String(bytes, StandardCharsets.UTF_8)) }.getOrElse { JSONArray() }
        } else {
            JSONArray()
        }
        if (existingArray.length() > MAX_LAYOUTS) return

        val mergedLayouts = mutableListOf<JSONObject>()
        val existingIds = mutableSetOf<String>()
        for (i in 0 until existingArray.length()) {
            val obj = existingArray.optJSONObject(i) ?: continue
            val canonical = canonicalLayout(obj) ?: continue
            if (!existingIds.add(canonical.getString("id"))) continue
            filterLayoutVariants(canonical, target, keepMatches = false, preserveMalformed = false)?.let(mergedLayouts::add)
        }

        val restoredIds = mutableSetOf<String>()
        for (i in 0 until backupArray.length()) {
            val restored = backupArray.optJSONObject(i)?.let(::canonicalLayout) ?: return
            if (!restoredIds.add(restored.getString("id"))) return
            val variants = restored.optJSONArray("layoutVariants") ?: return
            if (variants.length() == 0) return
            for (variantIndex in 0 until variants.length()) {
                val entry = variants.optJSONObject(variantIndex) ?: return
                if (variantMatchesTarget(entry, target) != true) return
            }

            val id = restored.optString("id").takeIf { it.isNotBlank() && it != "null" }
            val existing = id?.let { layoutId ->
                mergedLayouts.firstOrNull { it.optString("id") == layoutId }
            }
            if (existing == null) {
                mergedLayouts.add(JSONObject(restored.toString()))
                if (mergedLayouts.size > MAX_LAYOUTS) return
            } else {
                val destinationVariants = existing.optJSONArray("layoutVariants") ?: return
                for (variantIndex in 0 until variants.length()) {
                    destinationVariants.put(variants.getJSONObject(variantIndex))
                }
            }
        }

        if (mergedLayouts.size > MAX_LAYOUTS) return

        val merged = JSONArray()
        mergedLayouts.forEach { layout -> merged.put(canonicalLayout(layout) ?: return) }
        val mergedBytes = merged.toString().toByteArray(StandardCharsets.UTF_8)
        if (mergedBytes.size > MAX_CONFIG_BYTES) return
        copyBoundedAtomically(mergedBytes.inputStream(), layoutsFile, MAX_CONFIG_BYTES)
    }

    private fun filterLayoutVariants(
        layout: JSONObject,
        target: String,
        keepMatches: Boolean,
        preserveMalformed: Boolean,
    ): JSONObject? {
        fun malformed() = if (preserveMalformed) JSONObject(layout.toString()) else null
        val variants = layout.optJSONArray("layoutVariants") ?: return malformed()
        val selected = JSONArray()
        for (index in 0 until variants.length()) {
            val entry = variants.optJSONObject(index) ?: return malformed()
            val matches = variantMatchesTarget(entry, target) ?: return malformed()
            if (matches == keepMatches) selected.put(entry)
        }
        if (selected.length() == 0) return null
        return JSONObject(layout.toString()).apply { put("layoutVariants", selected) }
    }

    private fun variantMatchesTarget(entry: JSONObject, target: String): Boolean? {
        val displays = entry.optJSONObject("variant")?.optJSONObject("displays") ?: return null
        val mainType = displays.optJSONObject("mainScreenDisplay")?.optString("type") ?: return null
        if (mainType !in setOf("BUILT_IN", "EXTERNAL")) return null
        val secondary = displays.optJSONObject("secondaryScreenDisplay")
        val secondaryType = secondary?.optString("type")
        if (secondary != null && secondaryType !in setOf("BUILT_IN", "EXTERNAL")) return null
        val external = mainType == "EXTERNAL" || secondaryType == "EXTERNAL"
        return when (target) {
            "INTERNAL" -> !external
            "EXTERNAL" -> external
            else -> null
        }
    }

    private fun canonicalLayoutsArray(layouts: JSONArray): JSONArray? {
        if (layouts.length() > MAX_LAYOUTS) return null
        val canonical = JSONArray()
        val ids = mutableSetOf<String>()
        for (index in 0 until layouts.length()) {
            val layout = layouts.optJSONObject(index)?.let(::canonicalLayout) ?: return null
            if (!ids.add(layout.getString("id"))) return null
            canonical.put(layout)
        }
        return canonical
    }

    private fun canonicalControllerConfiguration(bytes: ByteArray): ByteArray? = runCatching {
        val text = String(bytes, StandardCharsets.UTF_8)
        val objectValue = JSONObject(text)
        val mappings = objectValue.optJSONArray("inputMapper") ?: return null
        require(mappings.length() <= 64)
        for (index in 0 until mappings.length()) {
            val mapping = mappings.optJSONObject(index) ?: error("Invalid input mapping")
            for (assignmentName in listOf("assignment", "altAssignment")) {
                val assignment = mapping.optJSONObject(assignmentName) ?: continue
                val modifiers = assignment.optJSONArray("modifierKeyCodes") ?: continue
                require(modifiers.length() <= 16)
            }
        }

        val dto = json.decodeFromString<ControllerConfigurationDto>(text)
        require(dto.inputMapper.size <= 64)
        require(dto.inputMapper.map { it.input }.distinct().size == dto.inputMapper.size)
        dto.inputMapper.forEach { mapping ->
            require(validAssignment(mapping.assignment))
            require(validAssignment(mapping.altAssignment))
        }
        val canonical = ControllerConfigurationDto.fromControllerConfiguration(dto.toControllerConfiguration())
        json.encodeToString(canonical).toByteArray(StandardCharsets.UTF_8)
    }.getOrNull()

    private fun validAssignment(assignment: InputConfigDto.AssignmentDto): Boolean = when (assignment) {
        InputConfigDto.AssignmentDto.None -> true
        is InputConfigDto.AssignmentDto.Key -> assignment.keyCode in 0..65535 &&
            assignment.modifierKeyCodes.size <= 16 && assignment.modifierKeyCodes.all { it in 0..65535 }
        is InputConfigDto.AssignmentDto.Axis -> assignment.axisCode in 0..65535
    }

    private fun canonicalLayout(layout: JSONObject): JSONObject? = runCatching {
        require(validLayoutShape(layout))
        val dto = gson.fromJson(layout.toString(), LayoutConfigurationDto::class.java)
        val model = dto.toModel()
        require(model.id != null && model.id != LayoutConfiguration.DEFAULT_ID)
        require(model.type == LayoutConfiguration.LayoutType.CUSTOM)
        require(model.name == null || model.name.length <= 256)
        require(model.opacity in 0..100)
        require(model.layoutVariants.isNotEmpty())
        JSONObject(gson.toJson(LayoutConfigurationDto.fromModel(model)))
    }.getOrNull()

    private fun validLayoutShape(layout: JSONObject): Boolean {
        val variants = layout.optJSONArray("layoutVariants") ?: return false
        if (variants.length() !in 1..MAX_VARIANTS_PER_LAYOUT) return false
        for (variantIndex in 0 until variants.length()) {
            val entry = variants.optJSONObject(variantIndex) ?: return false
            val variant = entry.optJSONObject("variant") ?: return false
            val uiSize = variant.optJSONObject("uiSize") ?: return false
            if (!boundedInt(uiSize, "x", 1, MAX_LAYOUT_COORDINATE) ||
                !boundedInt(uiSize, "y", 1, MAX_LAYOUT_COORDINATE)) return false
            val insets = variant.optJSONObject("insets") ?: return false
            if (listOf("left", "top", "right", "bottom").any {
                    !boundedInt(insets, it, 0, MAX_LAYOUT_COORDINATE)
                }) return false

            val folds = variant.optJSONArray("folds") ?: return false
            if (folds.length() > MAX_FOLDS_PER_VARIANT) return false
            for (foldIndex in 0 until folds.length()) {
                val bounds = folds.optJSONObject(foldIndex)?.optJSONObject("foldBounds") ?: return false
                if (!validRect(bounds)) return false
            }

            val displays = variant.optJSONObject("displays") ?: return false
            if (!validDisplay(displays.optJSONObject("mainScreenDisplay"))) return false
            val secondaryValue = displays.opt("secondaryScreenDisplay")
            if (secondaryValue != null && secondaryValue !== JSONObject.NULL &&
                !validDisplay(secondaryValue as? JSONObject)) return false

            val layoutObject = entry.optJSONObject("layout") ?: return false
            if (!validScreenLayout(layoutObject.optJSONObject("mainScreenLayoutDto")) ||
                !validScreenLayout(layoutObject.optJSONObject("secondaryScreenLayoutDto"))) return false
        }
        return true
    }

    private fun validScreenLayout(screen: JSONObject?): Boolean {
        screen ?: return false
        val componentsValue = screen.opt("components")
        if (componentsValue == null || componentsValue === JSONObject.NULL) return true
        val components = componentsValue as? JSONArray ?: return false
        if (components.length() > MAX_COMPONENTS_PER_SCREEN) return false
        for (index in 0 until components.length()) {
            val component = components.optJSONObject(index) ?: return false
            if (!validRect(component.optJSONObject("rect"))) return false
            val alpha = component.opt("alpha")
            if (alpha != null && alpha !== JSONObject.NULL) {
                val numeric = alpha as? Number ?: return false
                val value = numeric.toDouble()
                if (!value.isFinite() || value !in 0.0..1.0) return false
            }
        }
        return true
    }

    private fun validDisplay(display: JSONObject?): Boolean {
        display ?: return false
        return boundedInt(display, "id", 0, 4096) &&
            boundedInt(display, "width", 1, MAX_LAYOUT_COORDINATE) &&
            boundedInt(display, "height", 1, MAX_LAYOUT_COORDINATE)
    }

    private fun validRect(rect: JSONObject?): Boolean {
        rect ?: return false
        if (!boundedInt(rect, "x", -MAX_LAYOUT_COORDINATE, MAX_LAYOUT_COORDINATE) ||
            !boundedInt(rect, "y", -MAX_LAYOUT_COORDINATE, MAX_LAYOUT_COORDINATE) ||
            !boundedInt(rect, "width", 0, MAX_LAYOUT_COORDINATE) ||
            !boundedInt(rect, "height", 0, MAX_LAYOUT_COORDINATE)) return false
        val x = (rect.get("x") as Number).toLong()
        val y = (rect.get("y") as Number).toLong()
        val width = (rect.get("width") as Number).toLong()
        val height = (rect.get("height") as Number).toLong()
        return x + width <= MAX_LAYOUT_COORDINATE.toLong() * 2 &&
            y + height <= MAX_LAYOUT_COORDINATE.toLong() * 2
    }

    private fun boundedInt(objectValue: JSONObject, key: String, minimum: Int, maximum: Int): Boolean {
        val number = objectValue.opt(key) as? Number ?: return false
        val doubleValue = number.toDouble()
        if (!doubleValue.isFinite()) return false
        val longValue = number.toLong()
        return doubleValue == longValue.toDouble() && longValue in minimum.toLong()..maximum.toLong()
    }
}

