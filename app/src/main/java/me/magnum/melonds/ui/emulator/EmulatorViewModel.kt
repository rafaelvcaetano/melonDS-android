package me.magnum.melonds.ui.emulator

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.Maybe
import io.reactivex.Observable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.awaitSingleOrNull
import kotlinx.coroutines.rx2.rxMaybe
import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.common.Schedulers
import me.magnum.melonds.common.romprocessors.RomFileProcessorFactory
import me.magnum.melonds.common.runtime.FrameBufferProvider
import me.magnum.melonds.domain.model.BackgroundMode
import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.FpsCounterPosition
import me.magnum.melonds.domain.model.LayoutConfiguration
import me.magnum.melonds.domain.model.Orientation
import me.magnum.melonds.domain.model.Rom
import me.magnum.melonds.domain.model.RomInfo
import me.magnum.melonds.domain.model.RuntimeBackground
import me.magnum.melonds.domain.model.SaveStateSlot
import me.magnum.melonds.domain.model.emulator.FirmwareLaunchResult
import me.magnum.melonds.domain.model.emulator.RomLaunchResult
import me.magnum.melonds.domain.model.retroachievements.GameAchievementData
import me.magnum.melonds.domain.model.retroachievements.RAEvent
import me.magnum.melonds.domain.model.retroachievements.RASimpleAchievement
import me.magnum.melonds.domain.repositories.BackgroundRepository
import me.magnum.melonds.domain.repositories.CheatsRepository
import me.magnum.melonds.domain.repositories.LayoutsRepository
import me.magnum.melonds.domain.repositories.RetroAchievementsRepository
import me.magnum.melonds.domain.repositories.RomsRepository
import me.magnum.melonds.domain.repositories.SaveStatesRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.domain.services.EmulatorManager
import me.magnum.melonds.ui.emulator.firmware.FirmwarePauseMenuOption
import me.magnum.melonds.ui.emulator.model.EmulatorState
import me.magnum.melonds.ui.emulator.model.EmulatorUiEvent
import me.magnum.melonds.ui.emulator.model.PauseMenu
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
import kotlin.time.Duration.Companion.seconds

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
    private val frameBufferProvider: FrameBufferProvider,
    private val emulatorManager: EmulatorManager,
    private val schedulers: Schedulers
) : ViewModel() {

    private val sessionCoroutineScope = EmulatorSessionCoroutineScope()

    private val _currentSystemOrientation = MutableStateFlow<Orientation?>(null)

    private val _emulatorState = MutableStateFlow<EmulatorState>(EmulatorState.Uninitialized)
    val emulatorState = _emulatorState.asStateFlow()

    private val _layout = MutableStateFlow<LayoutConfiguration?>(null)

    private val _runtimeLayout = MutableStateFlow<RuntimeInputLayoutConfiguration?>(null)
    val runtimeLayout = _runtimeLayout.asStateFlow()

    private val _runtimeRendererConfiguration = MutableStateFlow<RuntimeRendererConfiguration?>(null)
    val runtimeRendererConfiguration = _runtimeRendererConfiguration.asStateFlow()

    private val _background = MutableStateFlow(RuntimeBackground.None)
    val background = _background.asStateFlow()

    private val _achievementTriggeredEvent = MutableSharedFlow<RAAchievement>(extraBufferCapacity = 5, onBufferOverflow = BufferOverflow.SUSPEND)
    val achievementTriggeredEvent = _achievementTriggeredEvent.asSharedFlow()

    private val _currentFps = MutableStateFlow<Int?>(null)
    val currentFps = _currentFps.asStateFlow()

    private val _toastEvent = EventSharedFlow<ToastEvent>()
    val toastEvent = _toastEvent.asSharedFlow()

    private val _uiEvent = EventSharedFlow<EmulatorUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    fun loadRom(rom: Rom) {
        sessionCoroutineScope.notifyNewSessionStarted()
        _currentFps.value = null
        _emulatorState.value = EmulatorState.LoadingRom
        sessionCoroutineScope.launch {
            launchRom(rom)
        }
    }

    fun loadRom(romUri: Uri) {
        sessionCoroutineScope.notifyNewSessionStarted()
        _currentFps.value = null
        _emulatorState.value = EmulatorState.LoadingRom
        sessionCoroutineScope.launch {
            val rom = getRomAtUri(romUri).awaitSingleOrNull()
            if (rom != null) {
                launchRom(rom)
            } else {
                _emulatorState.value = EmulatorState.RomNotFoundError
            }
        }
    }

    fun loadRom(romPath: String) {
        sessionCoroutineScope.notifyNewSessionStarted()
        _currentFps.value = null
        _emulatorState.value = EmulatorState.LoadingRom
        sessionCoroutineScope.launch {
            val rom = getRomAtPath(romPath).awaitSingleOrNull()
            if (rom != null) {
                launchRom(rom)
            } else {
                _emulatorState.value = EmulatorState.RomNotFoundError
            }
        }
    }

    private suspend fun launchRom(rom: Rom) = coroutineScope {
        val (cheats, achievementData) = awaitAll(
            async { getRomInfo(rom)?.let { getRomEnabledCheats(it) } ?: emptyList() },
            async { getRomAchievementData(rom) },
        )

        startObservingBackground()
        startObservingRuntimeInputLayoutConfiguration()
        startObservingRendererConfiguration()
        startObservingAchievementEvents()
        startObservingLayoutForRom(rom)

        val result = emulatorManager.loadRom(rom, cheats as List<Cheat>, achievementData as GameAchievementData)
        when (result) {
            is RomLaunchResult.LaunchFailedSramProblem,
            is RomLaunchResult.LaunchFailed -> {
                _emulatorState.value = EmulatorState.RomLoadError
            }
            is RomLaunchResult.LaunchSuccessful -> {
                if (!result.isGbaLoadSuccessful) {
                    _toastEvent.tryEmit(ToastEvent.GbaLoadFailed)
                }
                _emulatorState.value = EmulatorState.RunningRom(rom)
                startSession(rom)
                startTrackingFps()
            }
        }
    }

    fun loadFirmware(consoleType: ConsoleType) {
        sessionCoroutineScope.notifyNewSessionStarted()
        _currentFps.value = null
        _emulatorState.value = EmulatorState.LoadingFirmware
        sessionCoroutineScope.launch {
            startObservingBackground()
            startObservingRuntimeInputLayoutConfiguration()
            startObservingRendererConfiguration()
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

    fun setSystemOrientation(orientation: Orientation) {
        _currentSystemOrientation.value = orientation
    }

    fun onSettingsChanged() {
        val currentState = _emulatorState.value
        sessionCoroutineScope.launch {
            when (currentState) {
                is EmulatorState.RunningRom -> emulatorManager.updateRomEmulatorConfiguration(currentState.rom)
                is EmulatorState.RunningFirmware -> emulatorManager.updateFirmwareEmulatorConfiguration(currentState.console)
                else -> {
                    // Do nothing
                }
            }
        }
    }

    fun onCheatsChanged() {
        val rom = (_emulatorState.value as? EmulatorState.RunningRom)?.rom ?: return

        getRomInfo(rom)?.let {
            sessionCoroutineScope.launch {
                val cheats = cheatsRepository.getRomEnabledCheats(it).await()
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
                        RomPauseMenuOption.values().filter {
                            filterRomPauseMenuOption(it)
                        }
                    }
                    is EmulatorState.RunningFirmware -> {
                        FirmwarePauseMenuOption.values().toList()
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
                _toastEvent.emit(ToastEvent.ResetFailed)
            }
        }
    }

    fun stopEmulator() {
        emulatorManager.stopEmulator()
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
                    RomPauseMenuOption.RESET -> resetEmulator()
                    RomPauseMenuOption.EXIT -> _uiEvent.tryEmit(EmulatorUiEvent.CloseEmulator)
                }
            }
            is FirmwarePauseMenuOption -> {
                when (option) {
                    FirmwarePauseMenuOption.SETTINGS -> _uiEvent.tryEmit(EmulatorUiEvent.OpenScreen.SettingsScreen)
                    FirmwarePauseMenuOption.RESET -> resetEmulator()
                    FirmwarePauseMenuOption.EXIT -> _uiEvent.tryEmit(EmulatorUiEvent.CloseEmulator)
                }
            }
        }
    }

    fun onOpenRewind() {
        if (settingsRepository.isRewindEnabled()) {
            sessionCoroutineScope.launch {
                emulatorManager.pauseEmulator()
                val rewindWindow = emulatorManager.getRewindWindow()
                _uiEvent.emit(EmulatorUiEvent.ShowRewindWindow(rewindWindow))
            }
        } else {
            _toastEvent.tryEmit(ToastEvent.RewindNotEnabled)
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
                sessionCoroutineScope.launch {
                    emulatorManager.pauseEmulator()
                    val quickSlot = saveStatesRepository.getRomQuickSaveStateSlot(currentState.rom)
                    if (loadRomState(currentState.rom, quickSlot)) {
                        _toastEvent.emit(ToastEvent.QuickLoadSuccessful)
                    }
                    emulatorManager.resumeEmulator()
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
            val screenshot = frameBufferProvider.getScreenshot()
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

    private fun startObservingRuntimeInputLayoutConfiguration() {
        sessionCoroutineScope.launch {
            combine(
                _layout,
                _currentSystemOrientation,
                settingsRepository.showSoftInput(),
                settingsRepository.isTouchHapticFeedbackEnabled(),
                settingsRepository.getSoftInputOpacity(),
            ) { layout, orientation, showSoftInput, isHapticFeedbackEnabled, inputOpacity ->
                if (layout == null || orientation == null) {
                    null
                } else {
                    val layoutToUse = when (orientation) {
                        Orientation.PORTRAIT -> layout.portraitLayout
                        Orientation.LANDSCAPE -> layout.landscapeLayout
                    }

                    val opacity = if (layout.useCustomOpacity) {
                        layout.opacity
                    } else {
                        inputOpacity
                    }

                    RuntimeInputLayoutConfiguration(
                        showSoftInput = showSoftInput,
                        softInputOpacity = opacity,
                        isHapticFeedbackEnabled = isHapticFeedbackEnabled,
                        layoutOrientation = layout.orientation,
                        layout = layoutToUse,
                    )
                }
            }.collect(_runtimeLayout)
        }
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
            combine(_layout, _currentSystemOrientation) { layout, orientation ->
                if (layout == null || orientation == null) {
                    RuntimeBackground.None
                } else {
                    if (orientation == Orientation.PORTRAIT) {
                        loadBackground(layout.portraitLayout.backgroundId, layout.portraitLayout.backgroundMode)
                    } else {
                        loadBackground(layout.landscapeLayout.backgroundId, layout.landscapeLayout.backgroundMode)
                    }
                }
            }.collect(_background)
        }
    }

    private suspend fun startObservingLayoutForRom(rom: Rom) {
        _layout.value = null
        _background.value = RuntimeBackground.None

        val romLayoutId = rom.config.layoutId
        val layoutObservable = if (romLayoutId == null) {
            getGlobalLayoutObservable()
        } else {
            // Load and observe ROM layout but switch to global layout if not found
            layoutsRepository.getLayout(romLayoutId)
                    .flatMapObservable {
                        // Continue observing the ROM layout but if the observable completes, this means that it is no
                        // longer available. From that point on, start observing the global layout
                        layoutsRepository.observeLayout(romLayoutId).concatWith(getGlobalLayoutObservable())
                    }
                    .switchIfEmpty(getGlobalLayoutObservable())
        }

        sessionCoroutineScope.launch {
            layoutObservable.subscribeOn(schedulers.backgroundThreadScheduler).asFlow().collect(_layout)
        }
    }

    private fun startObservingRendererConfiguration() {
        sessionCoroutineScope.launch {
            settingsRepository.getVideoFiltering().collectLatest {
                _runtimeRendererConfiguration.value = RuntimeRendererConfiguration(it)
            }
        }
    }

    private fun startObservingLayoutForFirmware() {
        _layout.value = null
        _background.value = RuntimeBackground.None

        sessionCoroutineScope.launch {
            getGlobalLayoutObservable()
                .subscribeOn(schedulers.backgroundThreadScheduler)
                .asFlow()
                .collect(_layout)
        }
    }

    private suspend fun loadBackground(backgroundId: UUID?, mode: BackgroundMode): RuntimeBackground {
        return if (backgroundId == null) {
            RuntimeBackground(null, mode)
        } else {
            val message = backgroundsRepository.getBackground(backgroundId)
                    .subscribeOn(schedulers.backgroundThreadScheduler)
                    .materialize()
                    .await()

            RuntimeBackground(message.value, mode)
        }
    }

    private fun getGlobalLayoutObservable(): Observable<LayoutConfiguration> {
        return settingsRepository.observeSelectedLayoutId()
                .startWith(settingsRepository.getSelectedLayoutId())
                .switchMap { layoutId ->
                    layoutsRepository.getLayout(layoutId)
                            .flatMapObservable { layoutsRepository.observeLayout(layoutId).startWith(it) }
                            .switchIfEmpty(layoutsRepository.observeLayout(LayoutConfiguration.DEFAULT_ID))
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

    private suspend fun getRomEnabledCheats(romInfo: RomInfo): List<Cheat> {
        if (!settingsRepository.areCheatsEnabled()) {
            return emptyList()
        }

        return cheatsRepository.getRomEnabledCheats(romInfo).await()
    }

    private suspend fun getRomAchievementData(rom: Rom): GameAchievementData {
        if (!retroAchievementsRepository.isUserAuthenticated()) {
            return GameAchievementData.withDisabledRetroAchievementsIntegration()
        }

        return retroAchievementsRepository.getGameUserAchievements(rom.retroAchievementsHash, false).map { achievements ->
            achievements.filter { !it.isUnlocked }.map { RASimpleAchievement(it.achievement.id, it.achievement.memoryAddress) }
        }.fold(
            onSuccess = {
                val richPresenceDescription = retroAchievementsRepository.getGameRichPresencePatch(rom.retroAchievementsHash)
                GameAchievementData(true, it, richPresenceDescription)
            },
            onFailure = { GameAchievementData.withDisabledRetroAchievementsIntegration() }
        )
    }

    private fun onAchievementTriggered(achievementId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            retroAchievementsRepository.getAchievement(achievementId)
                .onSuccess {
                    if (it != null) {
                        _achievementTriggeredEvent.emit(it)
                        retroAchievementsRepository.awardAchievement(it, false)
                    }
                }
        }
    }

    private fun startSession(rom: Rom) {
        sessionCoroutineScope.launch {
            retroAchievementsRepository.startSession(rom.retroAchievementsHash)
            while (isActive) {
                // TODO: Should we pause the session if the app goes to background? If so, how?
                delay(2 * 60 * 1000) // 2 minutes
                val richPresenceDescription = MelonEmulator.getRichPresenceStatus()
                retroAchievementsRepository.sendSessionHeartbeat(rom.retroAchievementsHash, richPresenceDescription)
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
            RomPauseMenuOption.REWIND -> settingsRepository.isRewindEnabled()
            else -> true
        }
    }

    override fun onCleared() {
        super.onCleared()
        sessionCoroutineScope.cancel()
        if (_emulatorState.value.isRunning()) {
            emulatorManager.stopEmulator()
        }
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