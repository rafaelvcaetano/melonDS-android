package me.magnum.melonds.impl

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import me.magnum.melonds.common.uridelegates.UriHandler
import me.magnum.melonds.domain.model.AudioBitrate
import me.magnum.melonds.domain.model.AudioInterpolation
import me.magnum.melonds.domain.model.AudioLatency
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.ControllerConfiguration
import me.magnum.melonds.domain.model.DsExternalScreen
import me.magnum.melonds.domain.model.EmulatorConfiguration
import me.magnum.melonds.domain.model.FirmwareConfiguration
import me.magnum.melonds.domain.model.FpsCounterPosition
import me.magnum.melonds.domain.model.MacAddress
import me.magnum.melonds.domain.model.MicSource
import me.magnum.melonds.domain.model.RendererConfiguration
import me.magnum.melonds.domain.model.RomIconFiltering
import me.magnum.melonds.domain.model.SaveStateLocation
import me.magnum.melonds.domain.model.SizeUnit
import me.magnum.melonds.domain.model.SortingMode
import me.magnum.melonds.domain.model.SortingOrder
import me.magnum.melonds.domain.model.VideoFiltering
import me.magnum.melonds.domain.model.VideoRenderer
import me.magnum.melonds.domain.model.camera.DSiCameraSourceType
import me.magnum.melonds.domain.model.input.SoftInputBehaviour
import me.magnum.melonds.domain.model.layout.LayoutConfiguration
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.impl.dtos.input.ControllerConfigurationDto
import me.magnum.melonds.impl.input.ControllerConfigurationFactory
import me.magnum.melonds.ui.Theme
import me.magnum.melonds.utils.enumValueOfIgnoreCase
import java.io.File
import java.util.UUID
import kotlin.math.pow

class SharedPreferencesSettingsRepository(
    private val context: Context,
    private val preferences: SharedPreferences,
    private val controllerConfigurationFactory: ControllerConfigurationFactory,
    private val json: Json,
    private val uriHandler: UriHandler,
    preferencesCoroutineScope: CoroutineScope,
) : SettingsRepository, OnSharedPreferenceChangeListener {

    companion object {
        private const val TAG = "SPSettingsRepository"
        private const val CONTROLLER_CONFIG_FILE = "controller_config.json"
    }

    @OptIn(ExperimentalSerializationApi::class)
    private val controllerConfiguration by lazy {
        val initialConfiguration = try {
            val configFile = File(context.filesDir, CONTROLLER_CONFIG_FILE)
            configFile.inputStream().use {
                val loadedConfiguration = json.decodeFromStream<ControllerConfigurationDto>(it)
                loadedConfiguration.toControllerConfiguration()
            }
        } catch (_: Exception) {
            controllerConfigurationFactory.buildDefaultControllerConfiguration()
        }

        MutableStateFlow(initialConfiguration)
    }
    private val preferenceObservers: HashMap<String, PublishSubject<Any>> = HashMap()
    private val preferenceSharedFlows = mutableMapOf<String, MutableSharedFlow<Unit>>()
    private val renderConfigurationFlow: SharedFlow<RendererConfiguration>

    init {
        preferences.registerOnSharedPreferenceChangeListener(this)
        setDefaultThemeIfRequired()
        setDefaultMacAddressIfRequired()

        renderConfigurationFlow = combine(
            getVideoRenderer(),
            getVideoFiltering(),
            isThreadedRenderingEnabled(),
            getVideoInternalResolutionScaling(),
        ) { renderer, filtering, threadedRenderingEnabled, resolutionScaling ->
            RendererConfiguration(renderer, filtering, threadedRenderingEnabled, resolutionScaling)
        }.conflate().shareIn(preferencesCoroutineScope, SharingStarted.Lazily, replay = 1)
    }

    private fun setDefaultThemeIfRequired() {
        if (preferences.getString("theme", null) != null)
            return

        val defaultTheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "system" else "light"
        preferences.edit {
            putString("theme", defaultTheme)
        }
    }

    private fun setDefaultMacAddressIfRequired() {
        if (preferences.getString("internal_mac_address", null) != null) {
            return
        }

        val macAddress = MacAddress.randomDsAddress()
        preferences.edit {
            putString("internal_mac_address", macAddress.toString())
        }
    }

    override suspend fun getEmulatorConfiguration(): EmulatorConfiguration {
        val consoleType = getDefaultConsoleType()
        val useCustomBios = useCustomBios()
        val dsBiosDirUri = getDsBiosDirectory()
        val dsiBiosDirUri = getDsiBiosDirectory()

        // Ensure all BIOS dirs are set. DSi requires both dirs to be set
        if ((consoleType == ConsoleType.DS && useCustomBios && dsBiosDirUri == null) || (consoleType == ConsoleType.DSi && (dsBiosDirUri == null || dsiBiosDirUri == null)))
            throw IllegalStateException("BIOS directory not set")

        val dsDirDocument = dsBiosDirUri?.let {
            DocumentFile.fromTreeUri(context, it)
        }
        val dsiDirDocument = dsiBiosDirUri?.let {
            DocumentFile.fromTreeUri(context, it)
        }

        return EmulatorConfiguration(
            useCustomBios(),
            showBootScreen(),
            dsDirDocument?.findFile("bios7.bin")?.uri,
            dsDirDocument?.findFile("bios9.bin")?.uri,
            dsDirDocument?.findFile("firmware.bin")?.uri,
            dsiDirDocument?.findFile("bios7.bin")?.uri,
            dsiDirDocument?.findFile("bios9.bin")?.uri,
            dsiDirDocument?.findFile("firmware.bin")?.uri,
            dsiDirDocument?.findFile("nand.bin")?.uri,
            context.filesDir.absolutePath,
            getFastForwardSpeedMultiplier(),
            isRewindEnabled(),
            getRewindPeriod(),
            getRewindWindow(),
            isJitEnabled(),
            consoleType,
            isSoundEnabled(),
            getAudioInterpolation(),
            getAudioBitrate(),
            getVolume(),
            getAudioLatency(),
            getMicSource(),
            getFirmwareConfiguration(),
            renderConfigurationFlow.first(),
        )
    }

    override fun getTheme(): Theme {
        val themePreference = preferences.getString("theme", "light")!!
        return Theme.valueOf(themePreference.uppercase())
    }

    override fun getFastForwardSpeedMultiplier(): Float {
        val speedMultiplierPreference = preferences.getString("fast_forward_speed_multiplier", "-1")!!
        return speedMultiplierPreference.toFloat()
    }

    override fun isRewindEnabled(): Boolean {
        return preferences.getBoolean("enable_rewind", false)
    }

    override fun isSustainedPerformanceModeEnabled(): Boolean {
        return preferences.getBoolean("enable_sustained_performance", false)
    }

    override fun getRomSearchDirectories(): Array<Uri> {
        val dirPreference = preferences.getStringSet("rom_search_dirs", emptySet())
        return dirPreference?.map { it.toUri() }?.toTypedArray() ?: emptyArray()
    }

    override fun clearRomSearchDirectories() {
        preferences.edit {
            putStringSet("rom_search_dirs", null)
        }
    }

    override fun getRomIconFiltering(): RomIconFiltering {
        val romIconFilteringPreference = preferences.getString("rom_icon_filtering", "none")!!
        return enumValueOfIgnoreCase(romIconFilteringPreference)
    }

    override fun getRomCacheMaxSize(): SizeUnit {
        // Default cache size step is 3, or 1GB
        val cacheSizeStepPreference = preferences.getInt("rom_cache_max_size", 3)
        // Cache size is 128MB * (cacheSizeStepPreference ^ 2)
        return SizeUnit.MB(128) * 2.toDouble().pow(cacheSizeStepPreference).toLong()
    }
    override fun getDefaultConsoleType(): ConsoleType {
        val consoleTypePreference = preferences.getString("console_type", "ds")!!
        return enumValueOfIgnoreCase(consoleTypePreference)
    }

    override fun getFirmwareConfiguration(): FirmwareConfiguration {
        val birthdayPreference = preferences.getString("firmware_settings_birthday", "01/01")!!
        val parts = birthdayPreference.split("/")
        val birthday = if (parts.size != 2) {
            Pair(1, 1)
        } else {
            val day = parts[0].toIntOrNull() ?: 1
            val month = parts[1].toIntOrNull() ?: 1
            Pair(day, month)
        }

        val useCustomBios = useCustomBios()
        var macAddress: String? = null
        val randomizeMacAddress = if (useCustomBios) {
            preferences.getBoolean("custom_randomize_mac_address", false)
        } else {
            var randomize = preferences.getBoolean("internal_randomize_mac_address", false)

            if (!randomize) {
                macAddress = preferences.getString("internal_mac_address", null)
                // If the MAC address is not defined, enable MAC address randomization
                if (macAddress == null) {
                    randomize = true
                }
            }
            randomize
        }

        return FirmwareConfiguration(
                preferences.getString("firmware_settings_nickname", "Player")!!,
                preferences.getString("firmware_settings_message", "Hello!")!!,
                preferences.getString("firmware_settings_language", "1")!!.toInt(),
                preferences.getInt("firmware_settings_colour", 0),
                birthday.second,
                birthday.first,
                randomizeMacAddress,
                macAddress
        )
    }

    override fun useCustomBios(): Boolean {
        return preferences.getBoolean("use_custom_bios", false)
    }

    override fun getDsBiosDirectory(): Uri? {
        val dirPreference = preferences.getStringSet("bios_dir", null)?.firstOrNull()
        return dirPreference?.toUri()
    }

    override fun getDsiBiosDirectory(): Uri? {
        val dirPreference = preferences.getStringSet("dsi_bios_dir", null)?.firstOrNull()
        return dirPreference?.toUri()
    }

    override fun showBootScreen(): Boolean {
        return preferences.getBoolean("show_bios", false)
    }

    override fun isJitEnabled(): Boolean {
        val defaultJitEnabled = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
        return preferences.getBoolean("enable_jit", defaultJitEnabled)
    }

    override fun getVideoRenderer(): Flow<VideoRenderer> {
        return getOrCreatePreferenceSharedFlow("video_renderer") {
            val videoRendererPreference = preferences.getString("video_renderer", "software")!!
            VideoRenderer.valueOf(videoRendererPreference.uppercase())
        }
    }

    override fun getVideoInternalResolutionScaling(): Flow<Int> {
        return getOrCreatePreferenceSharedFlow("video_internal_resolution") {
            val internalResolutionPreference = preferences.getString("video_internal_resolution", "1")!!
            internalResolutionPreference.toIntOrNull() ?: 1
        }
    }

    override fun getVideoFiltering(): Flow<VideoFiltering> {
        return getOrCreatePreferenceSharedFlow("video_filtering") {
            val filteringPreference = preferences.getString("video_filtering", "none")!!
            VideoFiltering.valueOf(filteringPreference.uppercase())
        }
    }

    override fun isThreadedRenderingEnabled(): Flow<Boolean> {
        return getOrCreatePreferenceSharedFlow("enable_threaded_rendering") {
            preferences.getBoolean("enable_threaded_rendering", true)
        }
    }

    override fun getFpsCounterPosition(): FpsCounterPosition {
        val fpsCounterPreference = preferences.getString("fps_counter_position", "hidden")!!
        return FpsCounterPosition.valueOf(fpsCounterPreference.uppercase())
    }

    override fun getExternalDisplayScreen(): DsExternalScreen {
        val screenPref = preferences.getString("external_display_screen", "top")!!
        return when (screenPref) {
            "bottom" -> DsExternalScreen.BOTTOM
            "custom" -> DsExternalScreen.CUSTOM
            else -> DsExternalScreen.TOP
        }
    }

    override fun observeExternalDisplayScreen(): Flow<DsExternalScreen> {
        return getOrCreatePreferenceSharedFlow("external_display_screen") {
            getExternalDisplayScreen()
        }
    }

    override fun isExternalDisplayKeepAspectRationEnabled(): Boolean {
        return preferences.getBoolean("external_display_keep_ratio", true)
    }

    override fun observeExternalDisplayKeepAspectRationEnabled(): Flow<Boolean> {
        return getOrCreatePreferenceSharedFlow("external_display_keep_ratio") {
            isExternalDisplayKeepAspectRationEnabled()
        }
    }

    override fun isExternalDisplayRotateLeftEnabled(): Flow<Boolean> {
        return getOrCreatePreferenceSharedFlow("external_display_rotate_left") {
            preferences.getBoolean("external_display_rotate_left", false)
        }
    }

    override fun getDSiCameraSource(): DSiCameraSourceType {
        val dsiCameraSource = preferences.getString("dsi_camera_source", "physical_cameras")!!
        return DSiCameraSourceType.valueOf(dsiCameraSource.uppercase())
    }

    override fun getDSiCameraStaticImage(): Uri? {
        val staticImagePreference = preferences.getStringSet("dsi_camera_static_image", null)?.firstOrNull()
        return staticImagePreference?.toUri()
    }

    override fun isSoundEnabled(): Boolean {
        return preferences.getBoolean("sound_enabled", true)
    }

    private fun getRewindPeriod(): Int {
        return preferences.getInt("rewind_period", 10)
    }

    private fun getRewindWindow(): Int {
        return preferences.getInt("rewind_window", 6) * 10
    }

    private fun getVolume(): Int {
        return preferences.getInt("volume", 256).coerceIn(0, 256)
    }

    private fun getAudioInterpolation(): AudioInterpolation {
        val interpolationPreference = preferences.getString("audio_interpolation", "none")!!
        return enumValueOfIgnoreCase(interpolationPreference)
    }

    private fun getAudioBitrate(): AudioBitrate {
        val bitratePreference = preferences.getString("audio_bitrate", "auto")!!
        return enumValueOfIgnoreCase(bitratePreference)
    }

    override fun getAudioLatency(): AudioLatency {
        val audioLatencyPreference = preferences.getString("audio_latency", "medium")!!
        return enumValueOfIgnoreCase(audioLatencyPreference)
    }

    override fun getMicSource(): MicSource {
        val micSourcePreference = preferences.getString("mic_source", "blow")!!
        return enumValueOfIgnoreCase(micSourcePreference)
    }

    override fun getRomSortingMode(): SortingMode {
        val sortingMode = preferences.getString("rom_sorting_mode", "alphabetically")!!
        return SortingMode.valueOf(sortingMode.uppercase())
    }

    override fun getRomSortingOrder(): SortingOrder {
        val sortingOrder = preferences.getString("rom_sorting_order", null)
        return if (sortingOrder == null)
            getRomSortingMode().defaultOrder
        else
            SortingOrder.valueOf(sortingOrder.uppercase())
    }

    override fun saveNextToRomFile(): Boolean {
        return preferences.getBoolean("use_rom_dir", true)
    }

    override fun getSaveFileDirectory(): Uri? {
        val dirPreference = preferences.getStringSet("sram_dir", null)?.firstOrNull()
        return dirPreference?.toUri()
    }

    override fun getSaveFileDirectory(rom: Rom): Uri {
        return if (!saveNextToRomFile() && getSaveFileDirectory() != null) {
            getSaveFileDirectory()!!
        } else {
            if (rom.parentTreeUri != null) {
                getRomParentDirectory(rom)
            } else {
                // We don't know the ROM's directory, so we can't save next to it. Put save file in an app folder
                val externalFilesDir = context.getExternalFilesDir(null)
                val saveFileDirectory = File(externalFilesDir, "saves")
                if (!saveFileDirectory.isDirectory && !saveFileDirectory.mkdirs()) {
                    throw Exception("Could not create internal save directory")
                }

                Uri.fromFile(saveFileDirectory)
            }
        }
    }

    override fun getSaveStateLocation(rom: Rom): SaveStateLocation {
        val locationPreference = preferences.getString("save_state_location", "save_dir")!!
        return SaveStateLocation.valueOf(locationPreference.uppercase())
    }

    override fun getSaveStateDirectory(rom: Rom): Uri? {
        val saveStateLocation = getSaveStateLocation(rom)

        return when (saveStateLocation) {
            SaveStateLocation.SAVE_DIR -> getSaveFileDirectory(rom)
            SaveStateLocation.ROM_DIR -> getRomParentDirectory(rom)
            SaveStateLocation.INTERNAL_DIR -> {
                val saveStateDir = File(context.getExternalFilesDir(null), "savestates")
                if (!saveStateDir.isDirectory) {
                    saveStateDir.mkdirs()
                }
                DocumentFile.fromFile(saveStateDir).uri
            }
        }
    }

    private fun getRomParentDirectory(rom: Rom): Uri {
        return rom.parentTreeUri?.let {
            uriHandler.getUriTreeDocument(rom.parentTreeUri)?.uri
        } ?: throw Exception("Could not determine ROMs parent document")
    }

    override fun getControllerConfiguration(): ControllerConfiguration {
        return controllerConfiguration.value
    }

    override fun observeControllerConfiguration(): StateFlow<ControllerConfiguration> {
        return controllerConfiguration
    }

    override fun getSelectedLayoutId(): UUID {
        val id = preferences.getString("input_layout_id", null)
        return id?.let { UUID.fromString(it) } ?: LayoutConfiguration.DEFAULT_ID
    }

    override fun getExternalLayoutId(): UUID {
        val id = preferences.getString("external_layout_id", null)
        return id?.let { UUID.fromString(it) } ?: LayoutConfiguration.DEFAULT_EXTERNAL_ID
    }

    override fun getSoftInputBehaviour(): Flow<SoftInputBehaviour> {
        return getOrCreatePreferenceSharedFlow("soft_input_behaviour") {
            val preference = preferences.getString("soft_input_behaviour", "hide_system_buttons_when_controller_connected")

            when (preference) {
                "always_visible" -> SoftInputBehaviour.ALWAYS_VISIBLE
                "hide_system_buttons_when_controller_connected" -> SoftInputBehaviour.HIDE_SYSTEM_BUTTONS_WHEN_CONTROLLERS_CONNECTED
                "hide_mapped_buttons_when_controller_connected" -> SoftInputBehaviour.HIDE_ALL_BUTTONS_ASSIGNED_TO_CONNECTED_CONTROLLERS
                "always_invisible" -> SoftInputBehaviour.ALWAYS_INVISIBLE
                else -> SoftInputBehaviour.HIDE_SYSTEM_BUTTONS_WHEN_CONTROLLERS_CONNECTED
            }
        }
    }

    override fun isTouchHapticFeedbackEnabled(): Flow<Boolean> {
        return getOrCreatePreferenceSharedFlow("input_touch_haptic_feedback_enabled") {
            preferences.getBoolean("input_touch_haptic_feedback_enabled", true)
        }
    }

    override fun getTouchHapticFeedbackStrength(): Int {
        val strength = preferences.getInt("input_touch_haptic_feedback_strength", 30)
        return strength.coerceIn(1, 100)
    }

    override fun getSoftInputOpacity(): Flow<Int> {
        return getOrCreatePreferenceSharedFlow("input_opacity") {
            preferences.getInt("input_opacity", 50)
        }
    }

    override fun isRetroAchievementsRichPresenceEnabled(): Boolean {
        return preferences.getBoolean("ra_rich_presence", true)
    }

    override fun isRetroAchievementsHardcoreEnabled(): Boolean {
        return preferences.getBoolean("ra_hardcore_enabled", false)
    }

    override fun areCheatsEnabled(): Boolean {
        return preferences.getBoolean("cheats_enabled", false)
    }

    override fun observeRomSearchDirectories(): Flow<Array<Uri>> {
        return getOrCreatePreferenceSharedFlow("rom_search_dirs") {
            getRomSearchDirectories()
        }
    }

    override fun observeSelectedLayoutId(): Observable<UUID> {
        return getOrCreatePreferenceObservable("input_layout_id") {
            getSelectedLayoutId()
        }
    }

    override fun observeExternalLayoutId(): Observable<UUID> {
        return getOrCreatePreferenceObservable("external_layout_id") {
            getExternalLayoutId()
        }
    }

    override fun observeDSiCameraSource(): Flow<DSiCameraSourceType> {
        return getOrCreatePreferenceSharedFlow("dsi_camera_source") {
            getDSiCameraSource()
        }
    }

    override fun observeDSiCameraStaticImage(): Flow<Uri?> {
        return getOrCreatePreferenceSharedFlow("dsi_camera_static_image") {
            getDSiCameraStaticImage()
        }
    }

    override fun setDsBiosDirectory(directoryUri: Uri) {
        preferences.edit {
            putStringSet("bios_dir", setOf(directoryUri.toString()))
        }
    }

    override fun setDsiBiosDirectory(directoryUri: Uri) {
        preferences.edit {
            putStringSet("dsi_bios_dir", setOf(directoryUri.toString()))
        }
    }

    override fun addRomSearchDirectory(directoryUri: Uri) {
        preferences.edit {
            putStringSet("rom_search_dirs", setOf(directoryUri.toString()))
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun setControllerConfiguration(controllerConfiguration: ControllerConfiguration) {
        this.controllerConfiguration.value = controllerConfiguration

        try {
            val configFile = File(context.filesDir, CONTROLLER_CONFIG_FILE)
            val dto = ControllerConfigurationDto.fromControllerConfiguration(controllerConfiguration)
            configFile.outputStream().use {
                json.encodeToStream(dto, it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save controller configuration", e)
        }
    }

    override fun setRomSortingMode(sortingMode: SortingMode) {
        preferences.edit {
            putString("rom_sorting_mode", sortingMode.toString().lowercase())
        }
    }

    override fun setRomSortingOrder(sortingOrder: SortingOrder) {
        preferences.edit {
            putString("rom_sorting_order", sortingOrder.toString().lowercase())
        }
    }

    override fun setSelectedLayoutId(layoutId: UUID) {
        preferences.edit {
            putString("input_layout_id", layoutId.toString())
        }
    }

    override fun setExternalLayoutId(layoutId: UUID) {
        preferences.edit {
            putString("external_layout_id", layoutId.toString())
        }
    }

    override fun setExternalDisplayScreen(screen: DsExternalScreen) {
        val value = when (screen) {
            DsExternalScreen.TOP -> "top"
            DsExternalScreen.BOTTOM -> "bottom"
            DsExternalScreen.CUSTOM -> "custom"
        }
        preferences.edit {
            putString("external_display_screen", value)
        }
    }

    override fun setExternalDisplayKeepAspectRatioEnabled(enabled: Boolean) {
        preferences.edit {
            putBoolean("external_display_keep_ratio", enabled)
        }
    }

    override fun setExternalDisplayRotateLeftEnabled(enabled: Boolean) {
        preferences.edit {
            putBoolean("external_display_rotate_left", enabled)
        }
    }

    override fun observeTheme(): Observable<Theme> {
        return getOrCreatePreferenceObservable("theme") {
            getTheme()
        }
    }

    override fun observeRomIconFiltering(): Flow<RomIconFiltering> {
        return getOrCreatePreferenceSharedFlow("rom_icon_filtering") {
            getRomIconFiltering()
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

    private fun <T> getOrCreatePreferenceSharedFlow(preference: String, mapper: () -> T): Flow<T> {
        val preferenceFlow = preferenceSharedFlows.getOrPut(preference) {
            MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST).apply {
                // Immediately trigger an event to load the initial value
                tryEmit(Unit)
            }
        }

        return preferenceFlow.map { mapper() }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        val subject = preferenceObservers[key]
        subject?.onNext(Any())

        preferenceSharedFlows[key]?.tryEmit(Unit)
    }

    override fun observeRenderConfiguration(): Flow<RendererConfiguration> {
        return renderConfigurationFlow
    }
}