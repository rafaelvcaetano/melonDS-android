package me.magnum.melonds.ui.emulator

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.Maybe
import io.reactivex.Observable
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.rx2.asFlow
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.awaitSingleOrNull
import kotlinx.coroutines.rx2.rxMaybe
import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.common.Schedulers
import me.magnum.melonds.common.romprocessors.RomFileProcessorFactory
import me.magnum.melonds.domain.model.*
import me.magnum.melonds.domain.model.emulator.FirmwareLaunchResult
import me.magnum.melonds.domain.model.emulator.RomLaunchResult
import me.magnum.melonds.domain.model.retroachievements.GameAchievementData
import me.magnum.melonds.domain.model.retroachievements.RAEvent
import me.magnum.melonds.domain.model.retroachievements.RASimpleAchievement
import me.magnum.melonds.domain.repositories.*
import me.magnum.melonds.domain.services.EmulatorManager
import me.magnum.melonds.ui.emulator.firmware.FirmwarePauseMenuOption
import me.magnum.melonds.ui.emulator.model.EmulatorState
import me.magnum.melonds.ui.emulator.model.RuntimeInputLayoutConfiguration
import me.magnum.melonds.ui.emulator.model.RuntimeRendererConfiguration
import me.magnum.melonds.ui.emulator.rom.RomPauseMenuOption
import me.magnum.rcheevosapi.model.RAAchievement
import java.util.*
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

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

    private val _gbaLoadFailedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val gbaLoadFailedEvent = _gbaLoadFailedEvent.asSharedFlow()

    fun loadRom(rom: Rom) {
        sessionCoroutineScope.notifyNewSessionStarted()
        _emulatorState.value = EmulatorState.LoadingRom
        sessionCoroutineScope.launch {
            launchRom(rom)
        }
    }

    fun loadRom(romUri: Uri) {
        sessionCoroutineScope.notifyNewSessionStarted()
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
                    _gbaLoadFailedEvent.tryEmit(Unit)
                }
                _emulatorState.value = EmulatorState.RunningRom(rom)
                startSession(rom)
            }
        }
    }

    fun loadFirmware(consoleType: ConsoleType) {
        sessionCoroutineScope.notifyNewSessionStarted()
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

    fun isRewindEnabled(): Boolean {
        return settingsRepository.isRewindEnabled()
    }

    fun getRomInfo(rom: Rom): RomInfo? {
        val fileRomProcessor = romFileProcessorFactory.getFileRomProcessorForDocument(rom.uri)
        return fileRomProcessor?.getRomInfo(rom)
    }

    fun getRomSaveStateSlots(rom: Rom): List<SaveStateSlot> {
        return saveStatesRepository.getRomSaveStates(rom)
    }

    fun getRomQuickSaveStateSlot(rom: Rom): SaveStateSlot {
        return saveStatesRepository.getRomQuickSaveStateSlot(rom)
    }

    fun getRomSaveStateSlotUri(rom: Rom, saveState: SaveStateSlot): Uri {
        return saveStatesRepository.getRomSaveStateUri(rom, saveState)
    }

    fun setRomSaveStateSlotScreenshot(rom: Rom, saveState: SaveStateSlot, screenshot: Bitmap) {
        saveStatesRepository.setRomSaveStateScreenshot(rom, saveState, screenshot)
    }

    fun deleteRomSaveStateSlot(rom: Rom, saveState: SaveStateSlot) {
        saveStatesRepository.deleteRomSaveState(rom, saveState)
    }

    fun getRomAtPath(path: String): Maybe<Rom> {
        return rxMaybe {
            romsRepository.getRomAtPath(path)
        }
    }

    fun getRomAtUri(uri: Uri): Maybe<Rom> {
        return rxMaybe {
            romsRepository.getRomAtUri(uri)
        }
    }

    fun getRomPauseMenuOptions(): List<PauseMenuOption> {
        return RomPauseMenuOption.values().filter(this::filterRomPauseMenuOption)
    }

    fun getFirmwarePauseMenuOptions(): List<PauseMenuOption> {
        return FirmwarePauseMenuOption.values().toList()
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

    private fun filterRomPauseMenuOption(option: RomPauseMenuOption): Boolean {
        return when (option) {
            RomPauseMenuOption.REWIND -> settingsRepository.isRewindEnabled()
            else -> true
        }
    }

    override fun onCleared() {
        super.onCleared()
        sessionCoroutineScope.cancel()
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