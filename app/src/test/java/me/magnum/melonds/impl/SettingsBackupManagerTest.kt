package me.magnum.melonds.impl

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.magnum.melonds.domain.model.*
import me.magnum.melonds.domain.model.camera.DSiCameraSourceType
import me.magnum.melonds.domain.model.input.SoftInputBehaviour
import me.magnum.melonds.domain.model.render.RenderStrategy
import me.magnum.melonds.domain.model.rewind.RewindWindowPosition
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.impl.dtos.input.ControllerConfigurationDto
import me.magnum.melonds.impl.dtos.input.InputConfigDto
import me.magnum.melonds.ui.Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import org.json.JSONArray

/**
 * Minimal [SettingsRepository] stub. Only [reloadControllerConfiguration] is exercised by
 * [SettingsBackupManager]; every other member is unused by the manager and throws if called.
 */
private class FakeSettingsRepository : SettingsRepository {
    var reloadCalled = false
        private set

    private fun notNeeded(): Nothing = throw UnsupportedOperationException("Not needed for this test")

    override suspend fun getEmulatorConfiguration(): EmulatorConfiguration = notNeeded()
    override fun getTheme(): Theme = notNeeded()
    override fun getFastForwardSpeedMultiplier(): Float = notNeeded()
    override fun shouldMuteFastForwardAudio(): Boolean = notNeeded()
    override fun isRewindEnabled(): Boolean = notNeeded()
    override fun getRewindWindowPosition(): RewindWindowPosition = notNeeded()
    override fun isSustainedPerformanceModeEnabled(): Boolean = notNeeded()
    override fun getRomSearchDirectories(): Array<Uri> = notNeeded()
    override fun clearRomSearchDirectories() {}
    override fun getRomIconFiltering(): RomIconFiltering = notNeeded()
    override fun getRomCacheMaxSize(): SizeUnit = notNeeded()
    override fun getDefaultConsoleType(): ConsoleType = notNeeded()
    override fun getFirmwareConfiguration(): FirmwareConfiguration = notNeeded()
    override fun useCustomBios(): Boolean = notNeeded()
    override fun getDsBiosDirectory(): Uri? = notNeeded()
    override fun getDsiBiosDirectory(): Uri? = notNeeded()
    override fun showBootScreen(): Boolean = notNeeded()
    override fun isJitEnabled(): Boolean = notNeeded()
    override fun isRtcSyncToHostEnabled(): Boolean = notNeeded()
    override fun getVideoRenderer(): Flow<VideoRenderer> = notNeeded()
    override fun getVideoInternalResolutionScaling(): Flow<Int> = notNeeded()
    override fun getVideoFiltering(): Flow<VideoFiltering> = notNeeded()
    override fun isThreadedRenderingEnabled(): Flow<Boolean> = notNeeded()
    override fun getRenderStrategy(): Flow<RenderStrategy> = notNeeded()
    override fun getFpsCounterPosition(): FpsCounterPosition = notNeeded()
    override fun getDSiCameraSource(): DSiCameraSourceType = notNeeded()
    override fun getDSiCameraStaticImage(): Uri? = notNeeded()
    override fun isSoundEnabled(): Boolean = notNeeded()
    override fun getAudioLatency(): AudioLatency = notNeeded()
    override fun getMicSource(): MicSource = notNeeded()
    override fun getRomSortingMode(): SortingMode = notNeeded()
    override fun getRomSortingOrder(): SortingOrder = notNeeded()
    override fun saveNextToRomFile(): Boolean = notNeeded()
    override fun getSaveFileDirectory(): Uri? = notNeeded()
    override fun getSaveFileDirectory(rom: Rom): Uri = notNeeded()
    override fun getSaveStateLocation(rom: Rom): SaveStateLocation = notNeeded()
    override fun getSaveStateDirectory(rom: Rom): Uri? = notNeeded()
    override fun getControllerConfiguration(): ControllerConfiguration = notNeeded()
    override fun observeControllerConfiguration(): StateFlow<ControllerConfiguration> = notNeeded()
    override fun reloadControllerConfiguration() { reloadCalled = true }
    override fun getSelectedLayoutId(): UUID = notNeeded()
    override fun getSoftInputBehaviour(): Flow<SoftInputBehaviour> = notNeeded()
    override fun isTouchHapticFeedbackEnabled(): Flow<Boolean> = notNeeded()
    override fun getTouchHapticFeedbackStrength(): Int = notNeeded()
    override fun getSoftInputOpacity(): Flow<Int> = notNeeded()
    override fun isRetroAchievementsRichPresenceEnabled(): Boolean = notNeeded()
    override fun isRetroAchievementsHardcoreEnabled(): Boolean = notNeeded()
    override fun areRetroAchievementsActiveChallengeIndicatorsEnabled(): Boolean = notNeeded()
    override fun areRetroAchievementsProgressIndicatorsEnabled(): Boolean = notNeeded()
    override fun areRetroAchievementsLeaderboardIndicatorsEnabled(): Boolean = notNeeded()
    override fun areCheatsEnabled(): Boolean = notNeeded()
    override fun observeTheme(): Flow<Theme> = notNeeded()
    override fun observeRomIconFiltering(): Flow<RomIconFiltering> = notNeeded()
    override fun observeRomSearchDirectories(): Flow<Array<Uri>> = notNeeded()
    override fun observeSelectedLayoutId(): Flow<UUID> = notNeeded()
    override fun observeDSiCameraSource(): Flow<DSiCameraSourceType> = notNeeded()
    override fun observeDSiCameraStaticImage(): Flow<Uri?> = notNeeded()
    override fun setDsBiosDirectory(directoryUri: Uri) {}
    override fun setDsiBiosDirectory(directoryUri: Uri) {}
    override fun addRomSearchDirectory(directoryUri: Uri) {}
    override fun setControllerConfiguration(controllerConfiguration: ControllerConfiguration) {}
    override fun setRomSortingMode(sortingMode: SortingMode) {}
    override fun setRomSortingOrder(sortingOrder: SortingOrder) {}
    override fun setSelectedLayoutId(layoutId: UUID) {}
    override fun observeRenderConfiguration(): Flow<RendererConfiguration> = notNeeded()
}

/**
 * Exercises the backup/restore round trip using a plain, file-backed [DocumentFile]
 * (see [SettingsBackupManager.backupToDocument]/[restoreFromDocument]) instead of a real SAF
 * tree Uri, since a document tree provider isn't available under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
// Preserve the file-backed ContentResolver behavior used by these fixtures.
@Config(application = Application::class, sdk = [28])
class SettingsBackupManagerTest {

    private lateinit var context: Application
    private lateinit var preferences: SharedPreferences
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var manager: SettingsBackupManager
    private lateinit var backupRoot: DocumentFile
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        listOf(
            "controller_config.json", "controller_config.json.bak", "controller_config.json.restore.previous",
            "layouts.json", "layouts.json.bak", "layouts.json.restore.previous",
        ).forEach { File(context.filesDir, it).delete() }
        settingsRepository = FakeSettingsRepository()
        manager = SettingsBackupManager(context, preferences, settingsRepository, Gson(), json)

        val backupDir = File(context.cacheDir, "backup_test_${System.nanoTime()}").apply { mkdirs() }
        backupRoot = DocumentFile.fromFile(backupDir)
    }

    @Test
    fun `backup then restore round trips preferences, controller config and layouts`() {
        preferences.edit()
            .putBoolean("enable_jit", true)
            .putString("theme", "dark")
            .putString("fast_forward_speed_multiplier", "4")
            .commit()

        val controllerJson = json.encodeToString(
            ControllerConfigurationDto(
                listOf(InputConfigDto(Input.A, InputConfigDto.AssignmentDto.Key(null, 96))),
            ),
        )
        File(context.filesDir, "controller_config.json").writeText(controllerJson)

        val layoutsJson = "[${layoutJson("My layout", "BUILT_IN")}]"
        File(context.filesDir, "layouts.json").writeText(layoutsJson)

        manager.backupToDocument(backupRoot)

        assertTrue(File(backupRoot.uri.path!!, "settings.json").exists())
        assertTrue(File(backupRoot.uri.path!!, "controller_config.json").exists())
        assertTrue(File(backupRoot.uri.path!!, "layouts.json").exists())

        // Simulate a fresh install: wipe local state, then restore from the backup.
        preferences.edit().clear().commit()
        File(context.filesDir, "controller_config.json").delete()
        File(context.filesDir, "layouts.json").delete()

        manager.restoreFromDocument(backupRoot)

        assertEquals(true, preferences.getBoolean("enable_jit", false))
        assertEquals("dark", preferences.getString("theme", null))
        assertEquals("4", preferences.getString("fast_forward_speed_multiplier", null))
        val restoredController = json.decodeFromString<ControllerConfigurationDto>(
            File(context.filesDir, "controller_config.json").readText(),
        ).toControllerConfiguration()
        assertEquals(Input.A, restoredController.keyToInput(96))
        assertTrue(File(context.filesDir, "layouts.json").readText().contains("My layout"))
        assertTrue("restore should invalidate the in-memory controller config cache", settingsRepository.reloadCalled)
    }

    @Test
    fun `excluded preference keys are never overwritten by restore`() {
        preferences.edit().putString("ra_token", "original-secret").commit()

        manager.backupToDocument(backupRoot)
        // Simulate the token having changed locally after the backup was made.
        preferences.edit().putString("ra_token", "still-local-secret").commit()

        manager.restoreFromDocument(backupRoot)

        assertEquals("still-local-secret", preferences.getString("ra_token", null))
    }

    @Test
    fun `restore rejects a type change for a known preference`() {
        preferences.edit().putBoolean("enable_jit", true).commit()
        File(backupRoot.uri.path!!, "settings.json").writeText("""{"enable_jit":"not-a-boolean"}""")

        manager.restoreFromDocument(backupRoot)

        assertEquals(true, preferences.getBoolean("enable_jit", false))
    }

    @Test
    fun `restore rejects invalid enum strings and internal migration metadata`() {
        preferences.edit().putString("theme", "dark").putLong("last_version", 42).commit()
        File(backupRoot.uri.path!!, "settings.json").writeText(
            """{"theme":"bogus","last_version":9223372036854775807}"""
        )

        manager.restoreFromDocument(backupRoot)

        assertEquals("dark", preferences.getString("theme", null))
        assertEquals(42, preferences.getLong("last_version", 0))
    }

    @Test
    fun `restore rejects semantic string and integer values outside UI domains`() {
        preferences.edit()
            .putString("firmware_settings_language", "1")
            .putString("soft_input_behaviour", "always_visible")
            .putInt("rom_cache_max_size", 3)
            .commit()
        File(backupRoot.uri.path!!, "settings.json").writeText(
            """{"firmware_settings_language":"bogus","soft_input_behaviour":"hide_system_buttons_when_controllers_connected","rom_cache_max_size":-50}"""
        )

        manager.restoreFromDocument(backupRoot)

        assertEquals("1", preferences.getString("firmware_settings_language", null))
        assertEquals("always_visible", preferences.getString("soft_input_behaviour", null))
        assertEquals(3, preferences.getInt("rom_cache_max_size", -1))
    }

    @Test
    fun `restore accepts the actual persisted soft input value`() {
        File(backupRoot.uri.path!!, "settings.json").writeText(
            """{"soft_input_behaviour":"hide_mapped_buttons_when_controller_connected"}"""
        )
        manager.restoreFromDocument(backupRoot)
        assertEquals(
            "hide_mapped_buttons_when_controller_connected",
            preferences.getString("soft_input_behaviour", null),
        )
    }

    @Test
    fun `oversized settings backup is rejected without changing preferences`() {
        preferences.edit().putString("theme", "dark").commit()
        File(backupRoot.uri.path!!, "settings.json").writeText(
            "{\"theme\":\"light\",\"padding\":\"${"x".repeat(1024 * 1024)}\"}"
        )

        manager.restoreFromDocument(backupRoot)

        assertEquals("dark", preferences.getString("theme", null))
    }

    @Test
    fun `derived rom data is neither backed up nor restored`() {
        val local = File(context.filesDir, "rom_data.json").apply { writeText("trusted") }
        File(backupRoot.uri.path!!, "rom_data.json").writeText("attacker-controlled")

        manager.backupToDocument(backupRoot)
        manager.restoreFromDocument(backupRoot)

        assertEquals("trusted", local.readText())
    }

    @Test
    fun `restore excludes derived timestamps version skips and authorization bearing camera uri`() {
        File(backupRoot.uri.path!!, "settings.json").writeText(
            """{"ra_hash_library_last_updated":9223372036854775807,"github_updates_nightly_next_check_date":9223372036854775807,"github_updates_nightly_last_release_date":9223372036854775807,"github_updates_last_check":9223372036854775807,"github_updates_skip_version":"not-a-version","dsi_camera_static_image":["content://private/document/photo"],"github_check_for_updates":false}""",
        )

        manager.restoreFromDocument(backupRoot)

        assertFalse(preferences.contains("ra_hash_library_last_updated"))
        assertFalse(preferences.contains("github_updates_nightly_next_check_date"))
        assertFalse(preferences.contains("github_updates_nightly_last_release_date"))
        assertFalse(preferences.contains("github_updates_last_check"))
        assertFalse(preferences.contains("github_updates_skip_version"))
        assertFalse(preferences.contains("dsi_camera_static_image"))
        assertFalse(preferences.getBoolean("github_check_for_updates", true))
    }

    @Test
    fun `malformed controller backup preserves the existing consumable mapping`() {
        val existing = json.encodeToString(
            ControllerConfigurationDto(
                listOf(InputConfigDto(Input.B, InputConfigDto.AssignmentDto.Key(null, 97))),
            ),
        )
        val destination = File(context.filesDir, "controller_config.json").apply { writeText(existing) }
        File(backupRoot.uri.path!!, "controller_config.json").writeText("""{"inputs":[]}""")

        manager.restoreFromDocument(backupRoot)

        assertEquals(existing, destination.readText())
        val restored = json.decodeFromString<ControllerConfigurationDto>(destination.readText()).toControllerConfiguration()
        assertEquals(Input.B, restored.keyToInput(97))
        assertFalse("invalid restore must not reload the controller cache", settingsRepository.reloadCalled)
    }

    @Test
    fun `general layout restore rejects duplicate persistent ids atomically`() {
        val destination = File(context.filesDir, "layouts.json").apply {
            writeText("[${layoutJson("trusted", "BUILT_IN")}]")
        }
        val first = layoutJson("duplicate-a", "BUILT_IN")
        val firstId = UUID.nameUUIDFromBytes("duplicate-a".toByteArray()).toString()
        val secondId = UUID.nameUUIDFromBytes("duplicate-b".toByteArray()).toString()
        val second = layoutJson("duplicate-b", "EXTERNAL").replace(secondId, firstId)
        File(backupRoot.uri.path!!, "layouts.json").writeText("[$first,$second]")

        manager.restoreFromDocument(backupRoot)

        assertTrue(destination.readText().contains("trusted"))
        assertFalse(destination.readText().contains("duplicate-a"))
        assertFalse(destination.readText().contains("duplicate-b"))
    }

    @Test
    fun `oversized filtered layout is rejected without replacing current layouts`() {
        val current = File(context.filesDir, "layouts.json").apply { writeText("[{\"target\":\"INTERNAL\",\"name\":\"trusted\"}]") }
        File(backupRoot.uri.path!!, "internal_layout.json").writeText(
            "[{\"target\":\"INTERNAL\",\"padding\":\"${"x".repeat(8 * 1024 * 1024)}\"}]"
        )

        manager.restoreFilteredLayoutFromDocument(backupRoot, "internal_layout.json", "INTERNAL")

        assertTrue(current.readText().contains("trusted"))
    }

    @Test
    fun `filtered layout backup uses current display schema`() {
        File(context.filesDir, "layouts.json").writeText(
            "[${layoutJson("internal", "BUILT_IN")},${layoutJson("external", "EXTERNAL")}]"
        )

        manager.backupFilteredLayoutToDocument(backupRoot, "internal_layout.json", "INTERNAL")
        manager.backupFilteredLayoutToDocument(backupRoot, "external_layout.json", "EXTERNAL")

        val internal = JSONArray(File(backupRoot.uri.path!!, "internal_layout.json").readText())
        val external = JSONArray(File(backupRoot.uri.path!!, "external_layout.json").readText())
        assertEquals("internal", internal.getJSONObject(0).getString("name"))
        assertEquals("external", external.getJSONObject(0).getString("name"))
        assertEquals(1, internal.length())
        assertEquals(1, external.length())
    }

    @Test
    fun `filtered layout restore rejects variants for another display target`() {
        val current = File(context.filesDir, "layouts.json").apply {
            writeText("[${layoutJson("trusted", "BUILT_IN")}]")
        }
        File(backupRoot.uri.path!!, "internal_layout.json").writeText(
            "[${layoutJson("attacker", "EXTERNAL")}]"
        )

        manager.restoreFilteredLayoutFromDocument(backupRoot, "internal_layout.json", "INTERNAL")

        assertTrue(current.readText().contains("trusted"))
        assertFalse(current.readText().contains("attacker"))
    }

    @Test
    fun `filtered layout restore rejects malformed dto and preserves consumable layouts`() = runBlocking {
        val current = File(context.filesDir, "layouts.json").apply {
            writeText("[${layoutJson("trusted-internal", "BUILT_IN")},${layoutJson("trusted-external", "EXTERNAL")}]")
        }
        File(backupRoot.uri.path!!, "internal_layout.json").writeText(
            "[${layoutJson("attacker", "BUILT_IN").replace("\"type\":\"CUSTOM\"", "\"type\":\"BOGUS\"")}]",
        )

        manager.restoreFilteredLayoutFromDocument(backupRoot, "internal_layout.json", "INTERNAL")

        assertFalse(current.readText().contains("attacker"))
        val reloaded = InternalLayoutsRepository(context, Gson()).getLayouts().first()
        assertTrue(reloaded.any { it.name == "trusted-internal" })
        assertTrue(reloaded.any { it.name == "trusted-external" })
    }

    @Test
    fun `filtered layout restore rejects excessive component counts before dto expansion`() {
        val current = File(context.filesDir, "layouts.json").apply {
            writeText("[${layoutJson("trusted", "BUILT_IN")}]")
        }
        val component = """{"rect":{"x":0,"y":0,"width":1,"height":1},"component":"TOP_SCREEN","alpha":1.0,"onTop":false}"""
        val excessive = List(513) { component }.joinToString(",")
        val hostile = layoutJson("too-many-components", "BUILT_IN")
            .replaceFirst("\"components\":[]", "\"components\":[$excessive]")
        File(backupRoot.uri.path!!, "internal_layout.json").writeText("[$hostile]")

        manager.restoreFilteredLayoutFromDocument(backupRoot, "internal_layout.json", "INTERNAL")

        assertTrue(current.readText().contains("trusted"))
        assertFalse(current.readText().contains("too-many-components"))
    }

    @Test
    fun `filtered layout restore rejects more than the structural layout cap`() {
        val current = File(context.filesDir, "layouts.json").apply {
            writeText("[${layoutJson("trusted", "BUILT_IN")}]")
        }
        val excessive = List(257) { layoutJson("hostile-$it", "BUILT_IN") }.joinToString(",")
        File(backupRoot.uri.path!!, "internal_layout.json").writeText("[$excessive]")

        manager.restoreFilteredLayoutFromDocument(backupRoot, "internal_layout.json", "INTERNAL")

        assertTrue(current.readText().contains("trusted"))
        assertFalse(current.readText().contains("hostile-0"))
    }

    @Test
    fun `filtered layout restore rejects overflowing rectangles and non finite alpha`() {
        val current = File(context.filesDir, "layouts.json").apply {
            writeText("[${layoutJson("trusted", "BUILT_IN")}]")
        }
        val hostileComponent = """{"rect":{"x":2147483647,"y":0,"width":2147483647,"height":1},"component":"TOP_SCREEN","alpha":1e400,"onTop":false}"""
        val hostile = layoutJson("overflowing", "BUILT_IN")
            .replaceFirst("\"components\":[]", "\"components\":[$hostileComponent]")
        File(backupRoot.uri.path!!, "internal_layout.json").writeText("[$hostile]")

        manager.restoreFilteredLayoutFromDocument(backupRoot, "internal_layout.json", "INTERNAL")

        assertTrue(current.readText().contains("trusted"))
        assertFalse(current.readText().contains("overflowing"))
    }

    @Test
    fun `filtered layout restore recovers an interrupted previous file before reading`() {
        val destination = File(context.filesDir, "layouts.json").apply { delete() }
        val previous = File(context.filesDir, "layouts.json.restore.previous").apply {
            writeText("[${layoutJson("recovered", "BUILT_IN")}]")
        }
        File(backupRoot.uri.path!!, "internal_layout.json").writeText("not-json")

        manager.restoreFilteredLayoutFromDocument(backupRoot, "internal_layout.json", "INTERNAL")

        assertTrue(destination.isFile)
        assertTrue(destination.readText().contains("recovered"))
        assertFalse(previous.exists())
    }

    @Test
    fun `layout repository rolls back interrupted atomic restore before parsing`() = runBlocking {
        val destination = File(context.filesDir, "layouts.json").apply { writeText("partial-json") }
        File(context.filesDir, "layouts.json.bak").writeText(
            "[${layoutJson("atomic-recovered", "BUILT_IN")}],".dropLast(1),
        )

        val reloaded = InternalLayoutsRepository(context, Gson()).getLayouts().first()

        assertTrue(reloaded.any { it.name == "atomic-recovered" })
        assertFalse(destination.readText().contains("partial-json"))
        assertFalse(File(context.filesDir, "layouts.json.bak").exists())
    }

    private fun layoutJson(name: String, displayType: String): String {
        val id = UUID.nameUUIDFromBytes(name.toByteArray())
        return """{"id":"$id","name":"$name","type":"CUSTOM","orientation":"FOLLOW_SYSTEM","useCustomOpacity":false,"opacity":50,"layoutVariants":[{"variant":{"uiSize":{"x":1,"y":1},"insets":{"left":0,"top":0,"right":0,"bottom":0},"orientation":"PORTRAIT","folds":[],"displays":{"mainScreenDisplay":{"id":0,"type":"$displayType","width":1,"height":1},"secondaryScreenDisplay":null}},"layout":{"mainScreenLayoutDto":{"backgroundId":null,"backgroundMode":"FIT_CENTER","components":[]},"secondaryScreenLayoutDto":{"backgroundId":null,"backgroundMode":"FIT_CENTER","components":[]}}}]}"""
    }
}
