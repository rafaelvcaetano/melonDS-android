package me.magnum.melonds.ui.emulator

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.Maybe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow
import kotlinx.coroutines.rx2.awaitSingleOrNull
import kotlinx.coroutines.rx2.rxMaybe
import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.common.romprocessors.RomFileProcessorFactory
import me.magnum.melonds.common.runtime.ScreenshotFrameBufferProvider
import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.DsExternalScreen
import me.magnum.melonds.domain.model.FpsCounterPosition
import me.magnum.melonds.domain.model.RomInfo
import me.magnum.melonds.domain.model.RuntimeBackground
import me.magnum.melonds.domain.model.SaveStateSlot
import me.magnum.melonds.domain.model.emulator.EmulatorSessionUpdateAction
import me.magnum.melonds.domain.model.emulator.FirmwareLaunchResult
import me.magnum.melonds.domain.model.emulator.RomLaunchResult
import me.magnum.melonds.domain.model.layout.BackgroundMode
import me.magnum.melonds.domain.model.layout.LayoutConfiguration
import me.magnum.melonds.domain.model.layout.ScreenFold
import me.magnum.melonds.domain.model.retroachievements.GameAchievementData
import me.magnum.melonds.domain.model.retroachievements.RAEvent
import me.magnum.melonds.domain.model.retroachievements.RASimpleAchievement
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.domain.model.ui.Orientation
import me.magnum.melonds.domain.repositories.BackgroundRepository
import me.magnum.melonds.domain.repositories.CheatsRepository
import me.magnum.melonds.domain.repositories.LayoutsRepository
import me.magnum.melonds.domain.repositories.RetroAchievementsRepository
import me.magnum.melonds.domain.repositories.RomsRepository
import me.magnum.melonds.domain.repositories.SaveStatesRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.domain.services.EmulatorManager
import me.magnum.melonds.impl.emulator.EmulatorSession
import me.magnum.melonds.impl.layout.UILayoutProvider
import me.magnum.melonds.ui.emulator.firmware.FirmwarePauseMenuOption
import me.magnum.melonds.ui.emulator.model.EmulatorState
import me.magnum.melonds.ui.emulator.model.EmulatorUiEvent
import me.magnum.melonds.ui.emulator.model.ExternalDisplayConfiguration
import me.magnum.melonds.ui.emulator.model.LaunchArgs
import me.magnum.melonds.ui.emulator.model.PauseMenu
import me.magnum.melonds.ui.emulator.model.RAIntegrationEvent
import me.magnum.melonds.ui.emulator.model.RuntimeInputLayoutConfiguration
import me.magnum.melonds.ui.emulator.model.RuntimeRendererConfiguration
import me.magnum.melonds.ui.emulator.model.ToastEvent
import me.magnum.melonds.ui.emulator.rewind.model.RewindSaveState
import me.magnum.melonds.ui.emulator.rom.RomPauseMenuOption
import me.magnum.melonds.utils.EventSharedFlow
import me.magnum.rcheevosapi.model.RAAchievement
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EmulatorViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val romsRepository: RomsRepository,
    private val cheatsRepository: CheatsRepository,
    private val retroAchievementsRepository: RetroAchievementsRepository,
    private val romFileProcessorFactory: RomFileProcessorFactory,
    private val layoutsRepository: LayoutsRepository,
    private val backgroundsRepository: BackgroundRepository,
    private val saveStatesRepository: SaveStatesRepository,
    private val screenshotFrameBufferProvider: ScreenshotFrameBufferProvider,
    private val uiLayoutProvider: UILayoutProvider,
    private val emulatorManager: EmulatorManager,
    private val emulatorSession: EmulatorSession,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val sessionCoroutineScope = EmulatorSessionCoroutineScope()
    private var raSessionJob: Job? = null

    private val _emulatorState = MutableStateFlow<EmulatorState>(EmulatorState.Uninitialized)
    val emulatorState = _emulatorState.asStateFlow()

    private val _layout = MutableStateFlow<LayoutConfiguration?>(null)

    private val _runtimeLayout = MutableStateFlow<RuntimeInputLayoutConfiguration?>(null)
    val runtimeLayout = _runtimeLayout.asStateFlow()

    val controllerConfiguration = settingsRepository.observeControllerConfiguration()

    private val _runtimeRendererConfiguration = MutableStateFlow<RuntimeRendererConfiguration?>(null)
    val runtimeRendererConfiguration = _runtimeRendererConfiguration.asStateFlow()

    private val _externalDisplayScreen = MutableStateFlow<DsExternalScreen>(DsExternalScreen.TOP)

    private val _externalDisplayConfiguration = MutableStateFlow(ExternalDisplayConfiguration())
    val externalDisplayConfiguration = _externalDisplayConfiguration.asStateFlow()

    private val _background = MutableStateFlow(RuntimeBackground.None)
    val background = _background.asStateFlow()

    private val _externalBackground = MutableStateFlow(RuntimeBackground.None)
    val externalBackground = _externalBackground.asStateFlow()

    private val _achievementTriggeredEvent = MutableSharedFlow<RAAchievement>(extraBufferCapacity = 5, onBufferOverflow = BufferOverflow.SUSPEND)
    val achievementTriggeredEvent = _achievementTriggeredEvent.asSharedFlow()

    private val _currentFps = MutableStateFlow<Int?>(null)
    val currentFps = _currentFps.asStateFlow()

    private val _toastEvent = EventSharedFlow<ToastEvent>()
    val toastEvent = _toastEvent.asSharedFlow()

    private val _raIntegrationEvent = EventSharedFlow<RAIntegrationEvent>()
    val integrationEvent = _raIntegrationEvent.asSharedFlow()

    private val _uiEvent = EventSharedFlow<EmulatorUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    private var currentRom: Rom? = null

    init {
        viewModelScope.launch {
            _layout.filterNotNull().collect {
                uiLayoutProvider.setCurrentLayoutConfiguration(it)
            }
        }
        startObservingExternalDisplayConfiguration()

        val launchArgs = LaunchArgs.fromSavedStateHandle(savedStateHandle)
        if (launchArgs != null) {
            launchEmulator(launchArgs)
        } else {
            _uiEvent.tryEmit(EmulatorUiEvent.CloseEmulator)
        }
    }

    fun relaunchWithNewArgs(args: LaunchArgs) {
        if (_emulatorState.value.isRunning()) {
            stopEmulator()
        }
        launchEmulator(args)
    }

    private fun launchEmulator(args: LaunchArgs) {
        when (args) {
            is LaunchArgs.RomObject -> loadRom(args.rom)
            is LaunchArgs.RomUri -> loadRom(args.uri)
            is LaunchArgs.RomPath -> loadRom(args.path)
            is LaunchArgs.Firmware -> loadFirmware(args.consoleType)
        }
    }

    private fun loadRom(rom: Rom) {
        viewModelScope.launch {
            resetEmulatorState(EmulatorState.LoadingRom)
            sessionCoroutineScope.launch {
                launchRom(rom)
            }
        }
    }

    private fun loadRom(romUri: Uri) {
        viewModelScope.launch {
            resetEmulatorState(EmulatorState.LoadingRom)
            sessionCoroutineScope.launch {
                val rom = getRomAtUri(romUri).awaitSingleOrNull()
                if (rom != null) {
                    launchRom(rom)
                } else {
                    _emulatorState.value = EmulatorState.RomNotFoundError(romUri.toString())
                }
            }
        }
    }

    private fun loadRom(romPath: String) {
        viewModelScope.launch {
            resetEmulatorState(EmulatorState.LoadingRom)
            sessionCoroutineScope.launch {
                val rom = getRomAtPath(romPath).awaitSingleOrNull()
                if (rom != null) {
                    launchRom(rom)
                } else {
                    _emulatorState.value = EmulatorState.RomNotFoundError(romPath)
                }
            }
        }
    }

    private suspend fun launchRom(rom: Rom) = coroutineScope {
        currentRom = rom
        startEmulatorSession(EmulatorSession.SessionType.RomSession(rom))
        startObservingBackground()
        startObservingExternalBackground()
        startObservingRuntimeInputLayoutConfiguration()
        startObservingRendererConfiguration()
        startObservingAchievementEvents()
        startObservingExternalDisplayScreenForRom(rom)
        startObservingLayoutForRom(rom)
        startRetroAchievementsSession(rom)

        val cheats = getRomInfo(rom)?.let { getRomEnabledCheats(it) } ?: emptyList()
        val result = emulatorManager.loadRom(rom, cheats)
        when (result) {
            is RomLaunchResult.LaunchFailedRomNotFound,
            is RomLaunchResult.LaunchFailedSramProblem,
            is RomLaunchResult.LaunchFailed -> {
                _emulatorState.value = EmulatorState.RomLoadError
            }
            is RomLaunchResult.LaunchSuccessful -> {
                if (!result.isGbaLoadSuccessful) {
                    _toastEvent.tryEmit(ToastEvent.GbaLoadFailed)
                }
                _emulatorState.value = EmulatorState.RunningRom(rom)
                startTrackingFps()
                startTrackingPlayTime(rom)
            }
        }
    }

    private fun loadFirmware(consoleType: ConsoleType) {
        viewModelScope.launch {
            resetEmulatorState(EmulatorState.LoadingFirmware)
            startEmulatorSession(EmulatorSession.SessionType.FirmwareSession(consoleType))
            sessionCoroutineScope.launch {
                startObservingBackground()
                startObservingExternalBackground()
                startObservingRuntimeInputLayoutConfiguration()
                startObservingRendererConfiguration()
                startObservingExternalDisplayScreenForFirmware()
                startObservingLayoutForFirmware()

                val result = emulatorManager.loadFirmware(consoleType)
                when (result) {
                    is FirmwareLaunchResult.LaunchFailed -> {
                        _emulatorState.value = EmulatorState.FirmwareLoadError(result.reason)
                    }
                    FirmwareLaunchResult.LaunchSuccessful -> {
                        _emulatorState.value = EmulatorState.RunningFirmware(consoleType)
                        startTrackingFps()
                    }
                }
            }
        }
    }

    fun setSystemOrientation(orientation: Orientation) {
        uiLayoutProvider.updateCurrentOrientation(orientation)
    }

    fun setUiSize(width: Int, height: Int) {
        uiLayoutProvider.updateUiSize(width, height)
    }

    fun setScreenFolds(folds: List<ScreenFold>) {
        uiLayoutProvider.updateFolds(folds)
    }

    fun onSettingsChanged() {
        val currentState = _emulatorState.value
        sessionCoroutineScope.launch {
            val sessionUpdateActions = emulatorSession.updateRetroAchievementsSettings(
                retroAchievementsRepository.isUserAuthenticated(),
                settingsRepository.isRetroAchievementsHardcoreEnabled(),
            )

            when (currentState) {
                is EmulatorState.RunningRom -> emulatorManager.updateRomEmulatorConfiguration(currentState.rom)
                is EmulatorState.RunningFirmware -> emulatorManager.updateFirmwareEmulatorConfiguration(currentState.console)
                else -> {
                    // Do nothing
                }
            }

            dispatchSessionUpdateActions(sessionUpdateActions)
        }
    }

    fun onCheatsChanged() {
        val rom = (_emulatorState.value as? EmulatorState.RunningRom)?.rom ?: return

        getRomInfo(rom)?.let {
            sessionCoroutineScope.launch {
                val cheats = getRomEnabledCheats(it)
                emulatorManager.updateCheats(cheats)
            }
        }
    }

    fun pauseEmulator(showPauseMenu: Boolean) {
        sessionCoroutineScope.launch {
            emulatorManager.pauseEmulator()
            if (showPauseMenu) {
                val pauseOptions = when (_emulatorState.value) {
                    is EmulatorState.RunningRom -> {
                        RomPauseMenuOption.entries.filter {
                            filterRomPauseMenuOption(it)
                        }
                    }
                    is EmulatorState.RunningFirmware -> {
                        FirmwarePauseMenuOption.entries
                    }
                    else -> null
                }

                if (pauseOptions != null) {
                    _uiEvent.emit(EmulatorUiEvent.ShowPauseMenu(PauseMenu(pauseOptions)))
                }
            }
        }
    }

    fun resumeEmulator() {
        sessionCoroutineScope.launch {
            emulatorManager.resumeEmulator()
        }
    }

    fun resetEmulator() {
        if (_emulatorState.value.isRunning()) {
            sessionCoroutineScope.launch {
                emulatorManager.resetEmulator()
            }
        }
    }

    fun stopEmulator() {
        emulatorManager.stopEmulator()
        screenshotFrameBufferProvider.clearBuffer()
    }

    private fun startTrackingPlayTime(rom: Rom) {
        sessionCoroutineScope.launch {
            var lastTime = System.currentTimeMillis()
            while (isActive) {
                delay(1000)
                val now = System.currentTimeMillis()
                romsRepository.addRomPlayTime(rom, (now - lastTime).milliseconds)
                lastTime = now
            }
        }
    }

    fun onPauseMenuOptionSelected(option: PauseMenuOption) {
        when (option) {
            is RomPauseMenuOption -> {
                when (option) {
                    RomPauseMenuOption.SETTINGS -> _uiEvent.tryEmit(EmulatorUiEvent.OpenScreen.SettingsScreen)
                    RomPauseMenuOption.SAVE_STATE -> {
                        (_emulatorState.value as? EmulatorState.RunningRom)?.let {
                            val saveStateSlots = getRomSaveStateSlots(it.rom)
                            _uiEvent.tryEmit(EmulatorUiEvent.ShowRomSaveStates(saveStateSlots, EmulatorUiEvent.ShowRomSaveStates.Reason.SAVING))
                        }
                    }
                    RomPauseMenuOption.LOAD_STATE -> {
                        (_emulatorState.value as? EmulatorState.RunningRom)?.let {
                            val saveStateSlots = getRomSaveStateSlots(it.rom)
                            _uiEvent.tryEmit(EmulatorUiEvent.ShowRomSaveStates(saveStateSlots, EmulatorUiEvent.ShowRomSaveStates.Reason.LOADING))
                        }
                    }
                    RomPauseMenuOption.REWIND -> {
                        sessionCoroutineScope.launch {
                            val rewindWindow = emulatorManager.getRewindWindow()
                            _uiEvent.emit(EmulatorUiEvent.ShowRewindWindow(rewindWindow))
                        }
                    }
                    RomPauseMenuOption.CHEATS -> {
                        (_emulatorState.value as? EmulatorState.RunningRom)?.let {
                            getRomInfo(it.rom)?.let { romInfo ->
                                _uiEvent.tryEmit(EmulatorUiEvent.OpenScreen.CheatsScreen(romInfo))
                            }
                        }
                    }
                    RomPauseMenuOption.VIEW_ACHIEVEMENTS -> _uiEvent.tryEmit(EmulatorUiEvent.ShowAchievementList)
                    RomPauseMenuOption.QUICK_SETTINGS -> _uiEvent.tryEmit(EmulatorUiEvent.ShowQuickSettings)
                    RomPauseMenuOption.RESET -> resetEmulator()
                    RomPauseMenuOption.EXIT -> {
                        emulatorManager.stopEmulator()
                        _uiEvent.tryEmit(EmulatorUiEvent.CloseEmulator)
                    }
                }
            }
            is FirmwarePauseMenuOption -> {
                when (option) {
                    FirmwarePauseMenuOption.SETTINGS -> _uiEvent.tryEmit(EmulatorUiEvent.OpenScreen.SettingsScreen)
                    FirmwarePauseMenuOption.RESET -> resetEmulator()
                    FirmwarePauseMenuOption.EXIT -> {
                        emulatorManager.stopEmulator()
                        _uiEvent.tryEmit(EmulatorUiEvent.CloseEmulator)
                    }
                }
            }
        }
    }

    fun onOpenRewind() {
        if (!settingsRepository.isRewindEnabled()) {
            _toastEvent.tryEmit(ToastEvent.RewindNotEnabled)
            return
        }

        if (!emulatorSession.areSaveStateLoadsAllowed()) {
            _toastEvent.tryEmit(ToastEvent.RewindNotAvailableWhileRAHardcoreModeEnabled)
            return
        }

        sessionCoroutineScope.launch {
            emulatorManager.pauseEmulator()
            val rewindWindow = emulatorManager.getRewindWindow()
            _uiEvent.emit(EmulatorUiEvent.ShowRewindWindow(rewindWindow))
        }
    }

    fun rewindToState(rewindSaveState: RewindSaveState) {
        sessionCoroutineScope.launch {
            emulatorManager.loadRewindState(rewindSaveState)
        }
    }

    fun saveStateToSlot(slot: SaveStateSlot) {
        sessionCoroutineScope.launch(Dispatchers.IO) {
            (_emulatorState.value as? EmulatorState.RunningRom)?.let {
                if (!saveRomState(it.rom, slot)) {
                    _toastEvent.emit(ToastEvent.StateSaveFailed)
                }
                emulatorManager.resumeEmulator()
            }
        }
    }

    fun loadStateFromSlot(slot: SaveStateSlot) {
        if (!slot.exists) {
            _toastEvent.tryEmit(ToastEvent.StateStateDoesNotExist)
        } else {
            sessionCoroutineScope.launch {
                (_emulatorState.value as? EmulatorState.RunningRom)?.let {
                    if (!loadRomState(it.rom, slot)) {
                        _toastEvent.emit(ToastEvent.StateLoadFailed)
                    }
                    emulatorManager.resumeEmulator()
                }
            }
        }
    }

    fun doQuickSave() {
        val currentState = _emulatorState.value
        when (currentState) {
            is EmulatorState.RunningRom -> {
                sessionCoroutineScope.launch {
                    emulatorManager.pauseEmulator()
                    val quickSlot = saveStatesRepository.getRomQuickSaveStateSlot(currentState.rom)
                    if (saveRomState(currentState.rom, quickSlot)) {
                        _toastEvent.emit(ToastEvent.QuickSaveSuccessful)
                    }
                    emulatorManager.resumeEmulator()
                }
            }
            is EmulatorState.RunningFirmware -> {
                _toastEvent.tryEmit(ToastEvent.CannotSaveStateWhenRunningFirmware)
            }
            else -> {
                // Do nothing
            }
        }
    }

    fun doQuickLoad() {
        val currentState = _emulatorState.value
        when (currentState) {
            is EmulatorState.RunningRom -> {
                if (emulatorSession.areSaveStateLoadsAllowed()) {
                    sessionCoroutineScope.launch {
                        emulatorManager.pauseEmulator()
                        val quickSlot = saveStatesRepository.getRomQuickSaveStateSlot(currentState.rom)
                        if (loadRomState(currentState.rom, quickSlot)) {
                            _toastEvent.emit(ToastEvent.QuickLoadSuccessful)
                        }
                        emulatorManager.resumeEmulator()
                    }
                } else {
                    _toastEvent.tryEmit(ToastEvent.CannotUseSaveStatesWhenRAHardcoreIsEnabled)
                }
            }
            is EmulatorState.RunningFirmware -> {
                _toastEvent.tryEmit(ToastEvent.CannotLoadStateWhenRunningFirmware)
            }
            else -> {
                // Do nothing
            }
        }
    }

    fun deleteSaveStateSlot(slot: SaveStateSlot): List<SaveStateSlot>? {
        return (_emulatorState.value as? EmulatorState.RunningRom)?.let {
            saveStatesRepository.deleteRomSaveState(it.rom, slot)
            getRomSaveStateSlots(it.rom)
        }
    }

    private suspend fun saveRomState(rom: Rom, slot: SaveStateSlot): Boolean {
        val slotUri = saveStatesRepository.getRomSaveStateUri(rom, slot)
        return if (emulatorManager.saveState(slotUri)) {
            val screenshot = screenshotFrameBufferProvider.getScreenshot()
            saveStatesRepository.setRomSaveStateScreenshot(rom, slot, screenshot)
            true
        } else {
            false
        }
    }

    private suspend fun loadRomState(rom: Rom, slot: SaveStateSlot): Boolean {
        if (!slot.exists) {
            return false
        }

        val slotUri = saveStatesRepository.getRomSaveStateUri(rom, slot)
        return emulatorManager.loadState(slotUri)
    }

    private fun startObservingExternalDisplayConfiguration() {
        viewModelScope.launch {
            combine(
                _externalDisplayScreen,
                settingsRepository.isExternalDisplayRotateLeftEnabled(),
                settingsRepository.observeExternalDisplayKeepAspectRationEnabled(),
            ) { displayMode, rotateLeft, keepAspectRatio ->
                ExternalDisplayConfiguration(
                    displayMode = displayMode,
                    rotateLeft = rotateLeft,
                    keepAspectRatio = keepAspectRatio,
                )
            }.collect(_externalDisplayConfiguration)
        }
    }

    private fun startObservingRuntimeInputLayoutConfiguration() {
        sessionCoroutineScope.launch {
            combine(
                _layout,
                uiLayoutProvider.currentLayout,
                settingsRepository.getSoftInputBehaviour(),
                settingsRepository.isTouchHapticFeedbackEnabled(),
                settingsRepository.getSoftInputOpacity(),
            ) { layoutConfiguration, variant, softInputBehaviour, isHapticFeedbackEnabled, inputOpacity ->
                val layout = variant?.second
                if (layoutConfiguration == null || layout == null) {
                    null
                } else {
                    val opacity = if (layoutConfiguration.useCustomOpacity) {
                        layoutConfiguration.opacity
                    } else {
                        inputOpacity
                    }

                    RuntimeInputLayoutConfiguration(
                        softInputBehaviour = softInputBehaviour,
                        softInputOpacity = opacity,
                        isHapticFeedbackEnabled = isHapticFeedbackEnabled,
                        layoutOrientation = layoutConfiguration.orientation,
                        layout = layout,
                    )
                }
            }.collect(_runtimeLayout)
        }
    }

    private fun resetEmulatorState(newState: EmulatorState) {
        sessionCoroutineScope.notifyNewSessionStarted()
        emulatorSession.reset()
        raSessionJob = null
        _currentFps.value = null
        _emulatorState.value = newState
        _background.value = RuntimeBackground.None
        _externalBackground.value = RuntimeBackground.None
        _layout.value = null
        currentRom = null
    }

    private fun startObservingAchievementEvents() {
        sessionCoroutineScope.launch {
            emulatorManager.observeRetroAchievementEvents().collect {
                when (it) {
                    is RAEvent.OnAchievementPrimed -> { /* TODO: Show primed achievement */ }
                    is RAEvent.OnAchievementUnPrimed -> { /* TODO: Remove primed achievement */ }
                    is RAEvent.OnAchievementTriggered -> onAchievementTriggered(it.achievementId)
                }
            }
        }
    }

    private fun startObservingBackground() {
        sessionCoroutineScope.launch {
            combine(uiLayoutProvider.currentLayout, ensureEmulatorIsRunning()) { variant, _ ->
                val layout = variant?.second
                if (layout == null) {
                    RuntimeBackground.None
                } else {
                    loadBackground(layout.backgroundId, layout.backgroundMode)
                }
            }.collect(_background)
        }
    }

    private fun startObservingExternalBackground() {
        val romLayoutId = currentRom?.config?.externalLayoutId
        val layoutFlow = if (romLayoutId == null) {
            settingsRepository.observeExternalLayoutId().asFlow()
                .onStart { emit(settingsRepository.getExternalLayoutId()) }
                .flatMapLatest { layoutsRepository.observeLayout(it) }
        } else {
            layoutsRepository.observeLayout(romLayoutId)
                .onCompletion {
                    emitAll(
                        settingsRepository.observeExternalLayoutId().asFlow()
                            .onStart { emit(settingsRepository.getExternalLayoutId()) }
                            .flatMapLatest { layoutsRepository.observeLayout(it) }
                    )
                }
        }

        sessionCoroutineScope.launch {
            layoutFlow
                .map { layout ->
                    val entry = layout.layoutVariants.entries.firstOrNull()
                    entry?.let { loadBackground(it.value.backgroundId, it.value.backgroundMode) }
                        ?: RuntimeBackground.None
                }
                .collect(_externalBackground)
        }
    }

    private fun startObservingExternalDisplayScreenForRom(rom: Rom) {
        val externalScreen = rom.config.externalScreen
        if (externalScreen != null) {
            _externalDisplayScreen.value = externalScreen
        } else {
            sessionCoroutineScope.launch {
                settingsRepository.observeExternalDisplayScreen().collect(_externalDisplayScreen)
            }
        }
    }

    private fun startObservingLayoutForRom(rom: Rom) {
        val romLayoutId = rom.config.layoutId
        val layoutFlow = if (romLayoutId == null) {
            getGlobalLayoutFlow()
        } else {
            // Load and observe ROM layout but switch to global layout if the ROM layout stops existing
            layoutsRepository.observeLayout(romLayoutId)
                .onCompletion {
                    emitAll(getGlobalLayoutFlow())
                }
        }

        sessionCoroutineScope.launch {
            combine(layoutFlow, ensureEmulatorIsRunning()) { layout, _ ->
                layout
            }.collect(_layout)
        }
    }

    private fun startObservingRendererConfiguration() {
        sessionCoroutineScope.launch {
            settingsRepository.observeRenderConfiguration().collectLatest {
                _runtimeRendererConfiguration.value = RuntimeRendererConfiguration(it.videoFiltering, it.resolutionScaling)
            }
        }
    }

    private fun startObservingExternalDisplayScreenForFirmware() {
        sessionCoroutineScope.launch {
            settingsRepository.observeExternalDisplayScreen().collect(_externalDisplayScreen)
        }
    }

    private fun startObservingLayoutForFirmware() {
        _layout.value = null

        sessionCoroutineScope.launch {
            combine(getGlobalLayoutFlow(), ensureEmulatorIsRunning()) { layout, _ ->
                layout
            }.collect(_layout)
        }
    }

    private suspend fun loadBackground(backgroundId: UUID?, mode: BackgroundMode): RuntimeBackground {
        return if (backgroundId == null) {
            RuntimeBackground(null, mode)
        } else {
            val background = backgroundsRepository.getBackground(backgroundId)
            RuntimeBackground(background, mode)
        }
    }

    private fun getGlobalLayoutFlow(): Flow<LayoutConfiguration> {
        return settingsRepository.observeSelectedLayoutId().asFlow()
            .onStart { emit(settingsRepository.getSelectedLayoutId()) }
            .flatMapLatest {
                layoutsRepository.observeLayout(it)
                    .onCompletion {
                        emitAll(layoutsRepository.observeLayout(LayoutConfiguration.DEFAULT_ID))
                    }
            }
    }

    private fun getRomInfo(rom: Rom): RomInfo? {
        val fileRomProcessor = romFileProcessorFactory.getFileRomProcessorForDocument(rom.uri)
        return fileRomProcessor?.getRomInfo(rom)
    }

    private fun getRomSaveStateSlots(rom: Rom): List<SaveStateSlot> {
        return saveStatesRepository.getRomSaveStates(rom)
    }

    private fun getRomAtPath(path: String): Maybe<Rom> {
        return rxMaybe {
            romsRepository.getRomAtPath(path)
        }
    }

    private fun getRomAtUri(uri: Uri): Maybe<Rom> {
        return rxMaybe {
            romsRepository.getRomAtUri(uri)
        }
    }

    fun isSustainedPerformanceModeEnabled(): Boolean {
        return settingsRepository.isSustainedPerformanceModeEnabled()
    }

    fun getFpsCounterPosition(): FpsCounterPosition {
        return settingsRepository.getFpsCounterPosition()
    }

    fun getExternalDisplayScreen(): DsExternalScreen {
        return _externalDisplayScreen.value
    }

    fun setExternalDisplayScreen(screen: DsExternalScreen) {
        _externalDisplayScreen.value = screen
    }

    fun isExternalDisplayKeepAspectRatioEnabled(): Boolean {
        return settingsRepository.isExternalDisplayKeepAspectRationEnabled()
    }

    fun setExternalDisplayKeepAspectRatioEnabled(enabled: Boolean) {
        settingsRepository.setExternalDisplayKeepAspectRatioEnabled(enabled)
    }

    private suspend fun getRomEnabledCheats(romInfo: RomInfo): List<Cheat> {
        if (!settingsRepository.areCheatsEnabled() || !emulatorSession.areCheatsEnabled()) {
            return emptyList()
        }

        return cheatsRepository.getRomEnabledCheats(romInfo)
    }

    private suspend fun getRomAchievementData(rom: Rom): GameAchievementData {
        if (!retroAchievementsRepository.isUserAuthenticated()) {
            return GameAchievementData.withDisabledRetroAchievementsIntegration(GameAchievementData.IntegrationStatus.DISABLED_NOT_LOGGED_IN)
        }

        return retroAchievementsRepository.getGameUserAchievements(rom.retroAchievementsHash, emulatorSession.isRetroAchievementsHardcoreModeEnabled).fold(
            onSuccess = { achievements ->
                val gameSummary = retroAchievementsRepository.getGameSummary(rom.retroAchievementsHash)

                if (achievements != null) {
                    if (achievements.isEmpty()) {
                        GameAchievementData.withLimitedRetroAchievementsIntegration(
                            richPresencePatch = gameSummary?.richPresencePatch,
                            icon = gameSummary?.icon,
                        )
                    } else {
                        val lockedAchievements = achievements.filter { !it.isUnlocked }.map { RASimpleAchievement(it.achievement.id, it.achievement.memoryAddress) }
                        GameAchievementData.withFullRetroAchievementsIntegration(
                            lockedAchievements = lockedAchievements,
                            totalAchievementCount = achievements.size,
                            richPresencePatch = gameSummary?.richPresencePatch,
                            icon = gameSummary?.icon,
                        )
                    }
                } else {
                    GameAchievementData.withDisabledRetroAchievementsIntegration(
                        status = GameAchievementData.IntegrationStatus.DISABLED_GAME_NOT_FOUND,
                        icon = gameSummary?.icon,
                    )
                }
            },
            onFailure = {
                // Maybe we have the game summary cached. Could allow the icon to be displayed, which looks better
                val gameSummary = retroAchievementsRepository.getGameSummary(rom.retroAchievementsHash)
                GameAchievementData.withDisabledRetroAchievementsIntegration(GameAchievementData.IntegrationStatus.DISABLED_LOAD_ERROR, gameSummary?.icon)
            }
        )
    }

    private fun onAchievementTriggered(achievementId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            retroAchievementsRepository.getAchievement(achievementId).onSuccess { achievement ->
                if (achievement != null) {
                    retroAchievementsRepository.awardAchievement(achievement, emulatorSession.isRetroAchievementsHardcoreModeEnabled).onSuccess {
                        _achievementTriggeredEvent.emit(achievement)
                    }
                }
            }
        }
    }

    private fun startRetroAchievementsSession(rom: Rom) {
        sessionCoroutineScope.launch {
            val achievementData = getRomAchievementData(rom)
            emulatorSession.updateRetroAchievementsIntegrationStatus(achievementData.retroAchievementsIntegrationStatus)
            if (!achievementData.isRetroAchievementsIntegrationEnabled) {
                if (achievementData.retroAchievementsIntegrationStatus == GameAchievementData.IntegrationStatus.DISABLED_LOAD_ERROR) {
                    _raIntegrationEvent.tryEmit(RAIntegrationEvent.Failed(achievementData.icon))
                }

                return@launch
            }

            raSessionJob = launch {
                // Wait until the emulator has actually started
                ensureEmulatorIsRunning().firstOrNull()

                val startResult = retroAchievementsRepository.startSession(rom.retroAchievementsHash)
                if (startResult.isFailure) {
                    _raIntegrationEvent.tryEmit(RAIntegrationEvent.Failed(achievementData.icon))
                } else {
                    if (achievementData.hasAchievements) {
                        emulatorManager.setupAchievements(achievementData)
                        _raIntegrationEvent.tryEmit(
                            RAIntegrationEvent.Loaded(
                                icon = achievementData.icon,
                                unlockedAchievements = achievementData.unlockedAchievementCount,
                                totalAchievements = achievementData.totalAchievementCount,
                            )
                        )
                    } else {
                        _raIntegrationEvent.tryEmit(RAIntegrationEvent.LoadedNoAchievements(achievementData.icon))
                    }

                    while (isActive) {
                        // TODO: Should we pause the session if the app goes to background? If so, how?
                        delay(2.minutes)
                        val richPresenceDescription = MelonEmulator.getRichPresenceStatus()
                        retroAchievementsRepository.sendSessionHeartbeat(rom.retroAchievementsHash, richPresenceDescription)
                    }
                }
            }
        }
    }

    private fun startTrackingFps() {
        sessionCoroutineScope.launch {
            while (isActive) {
                delay(1.seconds)
                _currentFps.value = emulatorManager.getFps()
            }
        }
    }

    private fun filterRomPauseMenuOption(option: RomPauseMenuOption): Boolean {
        return when (option) {
            RomPauseMenuOption.REWIND -> settingsRepository.isRewindEnabled() && emulatorSession.areSaveStateLoadsAllowed()
            RomPauseMenuOption.LOAD_STATE -> emulatorSession.areSaveStateLoadsAllowed()
            RomPauseMenuOption.CHEATS -> emulatorSession.areCheatsEnabled()
            RomPauseMenuOption.VIEW_ACHIEVEMENTS -> emulatorSession.areRetroAchievementsEnabled()
            else -> true
        }
    }

    private fun ensureEmulatorIsRunning(): Flow<Unit> {
        return _emulatorState.filter { it.isRunning() }.take(1).map { }
    }

    private suspend fun startEmulatorSession(sessionType: EmulatorSession.SessionType) {
        val isUserAuthenticatedInRetroAchievements = retroAchievementsRepository.isUserAuthenticated()
        val isRetroAchievementsHardcoreModeEnabled = settingsRepository.isRetroAchievementsHardcoreEnabled()
        emulatorSession.startSession(
            areRetroAchievementsEnabled = isUserAuthenticatedInRetroAchievements,
            isRetroAchievementsHardcoreModeEnabled = isRetroAchievementsHardcoreModeEnabled,
            sessionType = sessionType,
        )
    }

    private fun dispatchSessionUpdateActions(actions: List<EmulatorSessionUpdateAction>) {
        actions.forEach {
            when (it) {
                EmulatorSessionUpdateAction.DisableRetroAchievements -> {
                    emulatorManager.unloadAchievements()
                    raSessionJob?.cancel()
                    raSessionJob = null
                }
                EmulatorSessionUpdateAction.EnableRetroAchievements -> {
                    (emulatorSession.currentSessionType() as? EmulatorSession.SessionType.RomSession)?.rom?.let { currentRom ->
                        startRetroAchievementsSession(currentRom)
                    }
                }
                EmulatorSessionUpdateAction.NotifyRetroAchievementsModeSwitch -> {
                    _toastEvent.tryEmit(ToastEvent.CannotSwitchRetroAchievementsMode)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sessionCoroutineScope.cancel()
        emulatorManager.cleanEmulator()
    }

    private class EmulatorSessionCoroutineScope : CoroutineScope {
        private var currentCoroutineContext: CoroutineContext = EmptyCoroutineContext

        override val coroutineContext: CoroutineContext get() = currentCoroutineContext

        fun notifyNewSessionStarted() {
            cancel()
            currentCoroutineContext = SupervisorJob() + Dispatchers.Main.immediate
        }

        fun cancel() {
            currentCoroutineContext.cancel()
        }
    }
}