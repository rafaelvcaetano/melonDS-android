package me.magnum.melonds.ui.emulator

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.common.romprocessors.RomFileProcessorFactory
import me.magnum.melonds.common.runtime.ScreenshotFrameBufferProvider
import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.FpsCounterPosition
import me.magnum.melonds.domain.model.RomInfo
import me.magnum.melonds.domain.model.RuntimeBackground
import me.magnum.melonds.domain.model.SaveStateSlot
import me.magnum.melonds.domain.model.emulator.EmulatorEvent
import me.magnum.melonds.domain.model.emulator.EmulatorSessionUpdateAction
import me.magnum.melonds.domain.model.emulator.FirmwareLaunchResult
import me.magnum.melonds.domain.model.emulator.RomLaunchResult
import me.magnum.melonds.domain.model.layout.BackgroundMode
import me.magnum.melonds.domain.model.layout.Insets
import me.magnum.melonds.domain.model.layout.LayoutConfiguration
import me.magnum.melonds.domain.model.layout.LayoutDisplayPair
import me.magnum.melonds.domain.model.layout.ScreenFold
import me.magnum.melonds.domain.model.retroachievements.GameAchievementData
import me.magnum.melonds.domain.model.retroachievements.RAEvent
import me.magnum.melonds.domain.model.retroachievements.RASimpleAchievement
import me.magnum.melonds.domain.model.retroachievements.RASimpleLeaderboard
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
import me.magnum.melonds.impl.emulator.recovery.EmulatorRecoveryRepository
import me.magnum.melonds.impl.emulator.recovery.RecoveryCause
import me.magnum.melonds.impl.emulator.recovery.RecoveryPrompt
import me.magnum.melonds.impl.emulator.recovery.RecoverySession
import me.magnum.melonds.impl.emulator.recovery.RecoverySessionType
import me.magnum.melonds.impl.emulator.recovery.shouldDisableHardcoreForRecovery
import me.magnum.melonds.impl.layout.UILayoutProvider
import me.magnum.melonds.ui.emulator.component.RetroAchievementsSubmissionHandler
import me.magnum.melonds.ui.emulator.firmware.FirmwarePauseMenuOption
import me.magnum.melonds.ui.emulator.model.EmulatorState
import me.magnum.melonds.ui.emulator.model.EmulatorUiEvent
import me.magnum.melonds.ui.emulator.model.LaunchArgs
import me.magnum.melonds.ui.emulator.model.PauseMenu
import me.magnum.melonds.ui.emulator.model.RAEventUi
import me.magnum.melonds.ui.emulator.model.RAIntegrationEvent
import me.magnum.melonds.ui.emulator.model.RumbleEvent
import me.magnum.melonds.ui.emulator.model.RuntimeInputLayoutConfiguration
import me.magnum.melonds.ui.emulator.model.RuntimeRendererConfiguration
import me.magnum.melonds.ui.emulator.model.ToastEvent
import me.magnum.melonds.ui.emulator.rewind.model.RewindSaveState
import me.magnum.melonds.ui.emulator.rom.RomPauseMenuOption
import me.magnum.melonds.utils.EventSharedFlow
import me.magnum.rcheevosapi.exception.UserTokenExpiredException
import me.magnum.rcheevosapi.model.RAUserAuth
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.roundToInt
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
    private val emulatorRecoveryRepository: EmulatorRecoveryRepository,
    private val retroAchievementsSubmissionHandler: RetroAchievementsSubmissionHandler,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val sessionCoroutineScope = EmulatorSessionCoroutineScope()
    private val sleepTransitionMutex = Mutex()
    private var raSessionJob: Job? = null
    private var pendingRecoveryRestore: RecoveryPrompt? = null
    private var automaticRecoveryInProgress = false
    private var sleepCheckpointFailed = false
    private var deviceSleepTransitionActive = false
    private var sleepPreparationJob: Job? = null

    private val _emulatorState = MutableStateFlow<EmulatorState>(EmulatorState.Uninitialized)
    val emulatorState = _emulatorState.asStateFlow()

    private val _layout = MutableStateFlow<LayoutConfiguration?>(null)

    private val _currentLayout = uiLayoutProvider.currentLayout.shareIn(viewModelScope, SharingStarted.Lazily)

    private val _runtimeLayout = MutableStateFlow<RuntimeInputLayoutConfiguration?>(null)
    val runtimeLayout = _runtimeLayout.asStateFlow()

    val controllerConfiguration = settingsRepository.observeControllerConfiguration()

    private val _runtimeRendererConfiguration = MutableStateFlow<RuntimeRendererConfiguration?>(null)
    val runtimeRendererConfiguration = _runtimeRendererConfiguration.asStateFlow()

    private val _mainScreenBackground = MutableStateFlow(RuntimeBackground.None)
    val mainScreenBackground = _mainScreenBackground.asStateFlow()

    private val _secondaryScreenBackground = MutableStateFlow(RuntimeBackground.None)
    val secondaryScreenBackground = _secondaryScreenBackground.asStateFlow()

    private val _rumbleEvent = MutableSharedFlow<RumbleEvent>(extraBufferCapacity = 100, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val rumbleEvent = _rumbleEvent.asSharedFlow()

    private val _achievementsEvent = MutableSharedFlow<RAEventUi>(extraBufferCapacity = 100, onBufferOverflow = BufferOverflow.SUSPEND)
    val achievementsEvent = _achievementsEvent.asSharedFlow()

    private val _currentFps = MutableStateFlow<Int?>(null)
    val currentFps = _currentFps.asStateFlow()

    private val _toastEvent = EventSharedFlow<ToastEvent>()
    val toastEvent = _toastEvent.asSharedFlow()

    private val _raIntegrationEvent = Channel<RAIntegrationEvent>(Channel.UNLIMITED)
    val integrationEvent = _raIntegrationEvent.receiveAsFlow()

    val pendingSubmissionsSummary = retroAchievementsSubmissionHandler.getPendingSubmissionsSummaryFlow()

    private val _uiEvent = EventSharedFlow<EmulatorUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    private val _recoveryPrompt = MutableStateFlow<RecoveryPrompt?>(null)
    val recoveryPrompt = _recoveryPrompt.asStateFlow()

    init {
        viewModelScope.launch {
            _layout.filterNotNull().collect {
                uiLayoutProvider.setCurrentLayoutConfiguration(it)
            }
        }

        viewModelScope.launch {
            initializeSession(LaunchArgs.fromSavedStateHandle(savedStateHandle))
        }
    }

    private suspend fun initializeSession(launchArgs: LaunchArgs?) {
        val recovery = withContext(Dispatchers.IO) {
            emulatorRecoveryRepository.getPendingRecovery()
        }
        val automaticRecoveryStarted = recovery?.automaticRestoreAllowed == true &&
            withContext(Dispatchers.IO) {
                emulatorRecoveryRepository.markAutomaticRecoveryStarted(recovery.session.id)
            }

        if (automaticRecoveryStarted) {
            automaticRecoveryInProgress = true
            pendingRecoveryRestore = recovery
            launchRecoverySession(recovery.session)
        } else if (recovery != null) {
            withContext(Dispatchers.IO) {
                emulatorRecoveryRepository.record("recovery_prompted")
            }
            _recoveryPrompt.value = recovery
            _emulatorState.value = EmulatorState.RecoveryPending
        } else if (launchArgs != null) {
            launchEmulator(launchArgs)
        } else {
            _uiEvent.emit(EmulatorUiEvent.CloseEmulator)
        }
    }

    fun relaunchWithNewArgs(args: LaunchArgs) {
        if (_emulatorState.value.isRunning()) {
            stopEmulator()
            emulatorRecoveryRepository.markClean("rom_switch")
        }
        launchEmulator(args)
    }

    fun onRomLaunchValidated(rom: Rom) {
        viewModelScope.launch {
            launchRom(rom)
        }
    }

    fun onFirmwareLaunchValidated(consoleType: ConsoleType) {
        viewModelScope.launch {
            launchFirmware(consoleType)
        }
    }

    private fun launchEmulator(args: LaunchArgs) {
        when (args) {
            is LaunchArgs.RomObject -> loadRom(args.rom)
            is LaunchArgs.RomUri -> loadRom(args.uri)
            is LaunchArgs.RomPath -> loadRom(args.path)
            is LaunchArgs.Firmware -> _emulatorState.value = EmulatorState.ValidatingFirmware(args.consoleType)
        }
    }

    private fun loadRom(rom: Rom) {
        viewModelScope.launch {
            resetEmulatorState(EmulatorState.LoadingRom)
            sessionCoroutineScope.launch {
                _emulatorState.value = EmulatorState.ValidatingRom(rom)
            }
        }
    }

    private fun loadRom(romUri: Uri) {
        viewModelScope.launch {
            resetEmulatorState(EmulatorState.LoadingRom)
            sessionCoroutineScope.launch {
                val rom = romsRepository.getRomAtUri(romUri)
                if (rom != null) {
                    _emulatorState.value = EmulatorState.ValidatingRom(rom)
                } else if (pendingRecoveryRestore != null) {
                    showRecoveryRestoreFailure("rom_not_found")
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
                val rom = romsRepository.getRomAtPath(romPath)
                if (rom != null) {
                    _emulatorState.value = EmulatorState.ValidatingRom(rom)
                } else if (pendingRecoveryRestore != null) {
                    showRecoveryRestoreFailure("rom_not_found")
                } else {
                    _emulatorState.value = EmulatorState.RomNotFoundError(romPath)
                }
            }
        }
    }

    private suspend fun launchRom(rom: Rom) = coroutineScope {
        val recovery = pendingRecoveryRestore
        startEmulatorSession(
            sessionType = EmulatorSession.SessionType.RomSession(rom),
            disableHardcore = shouldDisableHardcoreForRecovery(
                recoveryPending = recovery != null,
                recordedHardcore = recovery?.session?.hardcoreEnabled == true,
                automaticRestore = automaticRecoveryInProgress,
            ),
        )
        if (recovery == null) {
            emulatorRecoveryRepository.beginRomSession(
                rom = rom,
                hardcoreEnabled = emulatorSession.isRetroAchievementsHardcoreModeEnabled,
            )
        }
        startObservingMainScreenBackground()
        startObservingSecondaryScreenBackground()
        startObservingRuntimeInputLayoutConfiguration()
        startObservingRendererConfiguration()
        startObservingEmulatorEvents()
        startObservingAchievementEvents()
        startObservingLayoutForRom(rom)
        startRetroAchievementsSession(rom)

        val cheats = getRomInfo(rom)?.let { getRomEnabledCheats(it) } ?: emptyList()
        val result = emulatorManager.loadRom(rom, cheats)
        when (result) {
            is RomLaunchResult.LaunchFailedRomNotFound,
            is RomLaunchResult.LaunchFailedRomNotSupported,
            is RomLaunchResult.LaunchFailedEmulatorStart,
            is RomLaunchResult.LaunchFailedSramProblem,
            is RomLaunchResult.LaunchFailed -> {
                if (recovery != null) {
                    showRecoveryRestoreFailure("rom_relaunch_failed")
                } else {
                    emulatorRecoveryRepository.markClean("rom_launch_failed")
                    _emulatorState.value = EmulatorState.RomLoadError
                }
            }
            is RomLaunchResult.LaunchSuccessful -> {
                if (!result.isGbaLoadSuccessful) {
                    _toastEvent.tryEmit(ToastEvent.GbaLoadFailed)
                }
                if (recovery != null && !restoreCheckpoint(recovery)) {
                    return@coroutineScope
                }
                if (recovery != null) {
                    emulatorRecoveryRepository.beginRomSession(
                        rom = rom,
                        hardcoreEnabled = emulatorSession.isRetroAchievementsHardcoreModeEnabled,
                    )
                    emulatorRecoveryRepository.record(
                        "recovery_restored",
                        mapOf(
                            "sessionType" to RecoverySessionType.ROM.name,
                            "automatic" to automaticRecoveryInProgress,
                        ),
                    )
                    pendingRecoveryRestore = null
                    automaticRecoveryInProgress = false
                }
                _emulatorState.value = EmulatorState.RunningRom(rom)
                startTrackingFps()
                startTrackingPlayTime(rom)
            }
        }
    }

    private fun launchFirmware(consoleType: ConsoleType) {
        viewModelScope.launch {
            resetEmulatorState(EmulatorState.LoadingFirmware)
            val recovery = pendingRecoveryRestore
            startEmulatorSession(EmulatorSession.SessionType.FirmwareSession(consoleType))
            if (recovery == null) {
                emulatorRecoveryRepository.beginFirmwareSession(consoleType)
            }
            sessionCoroutineScope.launch {
                startObservingMainScreenBackground()
                startObservingSecondaryScreenBackground()
                startObservingRuntimeInputLayoutConfiguration()
                startObservingRendererConfiguration()
                startObservingLayoutForFirmware()
                startObservingEmulatorEvents()

                val result = emulatorManager.loadFirmware(consoleType)
                when (result) {
                    is FirmwareLaunchResult.LaunchFailed -> {
                        if (recovery != null) {
                            showRecoveryRestoreFailure("firmware_relaunch_failed")
                        } else {
                            emulatorRecoveryRepository.markClean("firmware_launch_failed")
                            _emulatorState.value = EmulatorState.FirmwareLoadError(result.reason)
                        }
                    }
                    FirmwareLaunchResult.LaunchFailedEmulatorStart -> {
                        if (recovery != null) {
                            showRecoveryRestoreFailure("firmware_emulator_start_failed")
                        } else {
                            emulatorRecoveryRepository.markClean("firmware_emulator_start_failed")
                            _emulatorState.value = EmulatorState.FirmwareStartError
                        }
                    }
                    FirmwareLaunchResult.LaunchSuccessful -> {
                        if (recovery != null && !restoreCheckpoint(recovery)) {
                            return@launch
                        }
                        if (recovery != null) {
                            emulatorRecoveryRepository.beginFirmwareSession(consoleType)
                            emulatorRecoveryRepository.record(
                                "recovery_restored",
                                mapOf(
                                    "sessionType" to RecoverySessionType.FIRMWARE.name,
                                    "automatic" to automaticRecoveryInProgress,
                                ),
                            )
                            pendingRecoveryRestore = null
                            automaticRecoveryInProgress = false
                        }
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

    fun setUiInsets(insets: Insets) {
        uiLayoutProvider.updateUiInsets(insets)
    }

    fun setScreenFolds(folds: List<ScreenFold>) {
        uiLayoutProvider.updateFolds(folds)
    }

    fun setConnectedDisplays(displays: LayoutDisplayPair) {
        uiLayoutProvider.updateDisplays(displays)
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

        sessionCoroutineScope.launch {
            getRomInfo(rom)?.let {
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

    fun startDeviceSleepTransition() {
        if (!_emulatorState.value.isRunning() ||
            deviceSleepTransitionActive && sleepPreparationJob?.isActive == true
        ) {
            return
        }
        deviceSleepTransitionActive = true
        emulatorRecoveryRepository.markDeviceSleepStarted()
        sleepPreparationJob = viewModelScope.launch {
            prepareForDeviceSleep()
        }
    }

    fun isDeviceSleepTransitionActive(): Boolean {
        return deviceSleepTransitionActive
    }

    fun abortDeviceSleepTransition() {
        sleepPreparationJob?.cancel()
        sleepPreparationJob = null
        deviceSleepTransitionActive = false
    }

    suspend fun finishDeviceSleepPreparation() {
        sleepPreparationJob?.let { job ->
            job.join()
            if (sleepPreparationJob === job) {
                sleepPreparationJob = null
            }
        }
    }

    suspend fun prepareForDeviceSleep(): Boolean = sleepTransitionMutex.withLock {
        if (!_emulatorState.value.isRunning()) {
            return@withLock false
        }

        emulatorRecoveryRepository.record("device_sleep_pause_requested")
        val pauseResult = emulatorManager.pauseEmulator()
        if (pauseResult != MelonEmulator.PauseResult.SUCCESS &&
            pauseResult != MelonEmulator.PauseResult.ALREADY_PAUSED
        ) {
            sleepCheckpointFailed = true
            emulatorRecoveryRepository.record(
                "device_sleep_pause_failed",
                mapOf("result" to pauseResult.name),
            )
            return@withLock false
        }

        emulatorRecoveryRepository.record("device_sleep_pause_acknowledged")
        val checkpointUri = emulatorRecoveryRepository.checkpointTempUri()
        val checkpointSaved = emulatorManager.saveState(checkpointUri)
        if (!checkpointSaved) {
            sleepCheckpointFailed = true
            emulatorRecoveryRepository.record("checkpoint_failed", mapOf("reason" to "native_save_failed"))
            return@withLock false
        }

        emulatorRecoveryRepository.commitCheckpoint().also { committed ->
            sleepCheckpointFailed = !committed
        }
    }

    suspend fun resumeAfterDeviceSleep(resumeEmulation: Boolean = true): Boolean = sleepTransitionMutex.withLock {
        val status = emulatorManager.getEmulatorStatus()
        if (status == MelonEmulator.EmulationStatus.STOPPED ||
            status == MelonEmulator.EmulationStatus.START_FAILED ||
            status == MelonEmulator.EmulationStatus.NOT_STARTED
        ) {
            _emulatorState.value = EmulatorState.RecoveryPending
            _recoveryPrompt.value = emulatorRecoveryRepository.recordUnexpectedTermination(
                reason = "native_status_${status.name}",
                cause = RecoveryCause.EmulatorStopped(status.name),
            )
            deviceSleepTransitionActive = false
            return@withLock false
        }

        if (resumeEmulation) {
            emulatorManager.resumeEmulator()
        }
        emulatorRecoveryRepository.markDeviceSleepResumed(
            mapOf(
                "status" to status.name,
                "emulationResumed" to resumeEmulation,
            ),
        )
        deviceSleepTransitionActive = false
        if (sleepCheckpointFailed) {
            sleepCheckpointFailed = false
            _toastEvent.emit(ToastEvent.RecoveryCheckpointFailed)
        }
        true
    }

    fun restoreRecovery() {
        val prompt = _recoveryPrompt.value ?: return
        if (!prompt.checkpointAvailable) {
            return
        }

        pendingRecoveryRestore = prompt
        automaticRecoveryInProgress = false
        _recoveryPrompt.value = null
        launchRecoverySession(prompt.session)
    }

    fun restartRecovery() {
        val prompt = _recoveryPrompt.value ?: return
        pendingRecoveryRestore = null
        automaticRecoveryInProgress = false
        _recoveryPrompt.value = null
        emulatorRecoveryRepository.discardRecovery("user_selected_restart")
        launchRecoverySession(prompt.session)
    }

    fun exitRecovery() {
        pendingRecoveryRestore = null
        automaticRecoveryInProgress = false
        _recoveryPrompt.value = null
        emulatorManager.stopEmulator()
        emulatorRecoveryRepository.discardRecovery("user_selected_exit")
        _uiEvent.tryEmit(EmulatorUiEvent.CloseEmulator)
    }

    fun exportRecoveryDiagnostics(destination: Uri) {
        viewModelScope.launch {
            val exported = emulatorRecoveryRepository.exportDiagnostics(destination)
            _toastEvent.emit(
                if (exported) ToastEvent.RecoveryDiagnosticsExported
                else ToastEvent.RecoveryDiagnosticsExportFailed
            )
        }
    }

    fun resetEmulator() {
        if (_emulatorState.value.isRunning()) {
            sessionCoroutineScope.launch {
                emulatorManager.resetEmulator()
                _achievementsEvent.emit(RAEventUi.Reset)
            }
        }
    }

    fun exitEmulator(force: Boolean = false) {
        if (!force && retroAchievementsSubmissionHandler.hasPendingSubmissions()) {
            _uiEvent.tryEmit(EmulatorUiEvent.ShowPendingSubmissionsDialog)
            retroAchievementsSubmissionHandler.retrySubmissionsImmediately()
        } else {
            stopEmulatorAndExit()
        }
    }

    private fun stopEmulator() {
        viewModelScope.launch {
            _achievementsEvent.emit(RAEventUi.Reset)
        }
        emulatorManager.stopEmulator()
        screenshotFrameBufferProvider.clearBuffer()
    }

    private fun stopEmulatorAndExit() {
        emulatorManager.stopEmulator()
        emulatorRecoveryRepository.markClean("user_exit")
        _uiEvent.tryEmit(EmulatorUiEvent.CloseEmulator)
    }

    private fun launchRecoverySession(session: RecoverySession) {
        when (session.type) {
            RecoverySessionType.ROM -> {
                val romUri = session.romUri
                if (romUri == null) {
                    showRecoveryRestoreFailure("missing_rom_uri")
                } else {
                    loadRom(Uri.parse(romUri))
                }
            }
            RecoverySessionType.FIRMWARE -> {
                val consoleType = session.consoleType?.let {
                    runCatching { ConsoleType.valueOf(it) }.getOrNull()
                }
                if (consoleType == null) {
                    showRecoveryRestoreFailure("missing_console_type")
                } else {
                    launchFirmware(consoleType)
                }
            }
        }
    }

    private suspend fun restoreCheckpoint(prompt: RecoveryPrompt): Boolean {
        val checkpointUri = emulatorRecoveryRepository.checkpointUri()
        if (checkpointUri == null) {
            showRecoveryRestoreFailure("checkpoint_missing")
            return false
        }

        val restored = emulatorManager.loadState(checkpointUri)
        if (!restored) {
            showRecoveryRestoreFailure("native_load_failed")
            return false
        }

        if (prompt.session.hardcoreEnabled && !automaticRecoveryInProgress) {
            emulatorRecoveryRepository.record("hardcore_disabled_for_recovery")
        }
        return true
    }

    private fun showRecoveryRestoreFailure(detail: String) {
        emulatorManager.stopEmulator()
        emulatorRecoveryRepository.record("recovery_restore_failed", mapOf("detail" to detail))
        val originalPrompt = pendingRecoveryRestore ?: _recoveryPrompt.value
        pendingRecoveryRestore = null
        automaticRecoveryInProgress = false
        _emulatorState.value = EmulatorState.RecoveryPending
        _recoveryPrompt.value = originalPrompt?.copy(cause = RecoveryCause.RestoreFailed(detail))
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
                            _uiEvent.emit(EmulatorUiEvent.ShowRewindWindow(rewindWindow, settingsRepository.getRewindWindowPosition()))
                        }
                    }
                    RomPauseMenuOption.CHEATS -> {
                        (_emulatorState.value as? EmulatorState.RunningRom)?.let {
                            sessionCoroutineScope.launch {
                                getRomInfo(it.rom)?.let { romInfo ->
                                    _uiEvent.tryEmit(EmulatorUiEvent.OpenScreen.CheatsScreen(romInfo))
                                }
                            }
                        }
                    }
                    RomPauseMenuOption.VIEW_ACHIEVEMENTS -> _uiEvent.tryEmit(EmulatorUiEvent.ShowAchievementList)
                    RomPauseMenuOption.RESET -> resetEmulator()
                    RomPauseMenuOption.EXIT -> exitEmulator(force = false)
                }
            }
            is FirmwarePauseMenuOption -> {
                when (option) {
                    FirmwarePauseMenuOption.SETTINGS -> _uiEvent.tryEmit(EmulatorUiEvent.OpenScreen.SettingsScreen)
                    FirmwarePauseMenuOption.RESET -> resetEmulator()
                    FirmwarePauseMenuOption.EXIT -> {
                        emulatorManager.stopEmulator()
                        emulatorRecoveryRepository.markClean("user_exit")
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
            _uiEvent.emit(EmulatorUiEvent.ShowRewindWindow(rewindWindow, settingsRepository.getRewindWindowPosition()))
        }
    }

    fun rewindToState(rewindSaveState: RewindSaveState) {
        sessionCoroutineScope.launch {
            emulatorManager.loadRewindState(rewindSaveState)
        }
    }

    fun saveStateToSlot(slot: SaveStateSlot) {
        sessionCoroutineScope.launch {
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
        if (!emulatorManager.saveState(slotUri)) {
            return false
        }

        // Capture and store the screenshot asynchronously
        sessionCoroutineScope.launch {
            // Delete old screenshot immediately
            saveStatesRepository.deleteRomSaveStateScreenshot(rom, slot)
            val screenshotCaptured = emulatorManager.takeScreenshot()

            if (screenshotCaptured) {
                val screenshot = screenshotFrameBufferProvider.getScreenshot()
                saveStatesRepository.setRomSaveStateScreenshot(rom, slot, screenshot)
            }
        }

        return true
    }

    private suspend fun loadRomState(rom: Rom, slot: SaveStateSlot): Boolean {
        if (!slot.exists) {
            return false
        }

        val slotUri = saveStatesRepository.getRomSaveStateUri(rom, slot)
        val success = emulatorManager.loadState(slotUri)
        if (success) {
            _achievementsEvent.emit(RAEventUi.Reset)
        }

        return success
    }

    private fun startObservingRuntimeInputLayoutConfiguration() {
        sessionCoroutineScope.launch {
            combine(
                _layout,
                _currentLayout,
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
        _mainScreenBackground.value = RuntimeBackground.None
        _secondaryScreenBackground.value = RuntimeBackground.None
        _layout.value = null
    }

    private fun startObservingEmulatorEvents() {
        sessionCoroutineScope.launch {
            emulatorManager.emulatorEvents.collect {
                when (it) {
                    is EmulatorEvent.RumbleStart -> _rumbleEvent.tryEmit(RumbleEvent.RumbleStart(it.duration))
                    EmulatorEvent.RumbleStop -> _rumbleEvent.tryEmit(RumbleEvent.RumbleStop)
                    is EmulatorEvent.Stop -> handleEmulatorStop(it.reason)
                }
            }
        }
    }

    private suspend fun handleEmulatorStop(reason: EmulatorEvent.Stop.Reason) {
        if (reason == EmulatorEvent.Stop.Reason.PowerOff && !deviceSleepTransitionActive) {
            emulatorManager.stopEmulator()
            emulatorRecoveryRepository.markClean("emulated_power_off")
            _uiEvent.tryEmit(EmulatorUiEvent.CloseEmulator)
            return
        }
        when (reason) {
            EmulatorEvent.Stop.Reason.GBAModeNotSupported -> _toastEvent.tryEmit(ToastEvent.GbaModeNotSupported)
            EmulatorEvent.Stop.Reason.BadExceptionRegion -> _toastEvent.tryEmit(ToastEvent.InternalError)
            EmulatorEvent.Stop.Reason.PowerOff -> Unit
        }
        emulatorManager.stopEmulator()
        abortDeviceSleepTransition()
        _emulatorState.value = EmulatorState.RecoveryPending
        _recoveryPrompt.value = emulatorRecoveryRepository.recordUnexpectedStop(reason)
        if (_recoveryPrompt.value == null) {
            _uiEvent.tryEmit(EmulatorUiEvent.CloseEmulator)
        }
    }

    private fun startObservingAchievementEvents() {
        sessionCoroutineScope.launch {
            emulatorManager.observeRetroAchievementEvents().collect {
                when (it) {
                    is RAEvent.OnAchievementPrimed -> onAchievementPrimed(it.achievementId)
                    is RAEvent.OnAchievementUnPrimed -> onAchievementUnPrimed(it.achievementId)
                    is RAEvent.OnAchievementTriggered -> onAchievementTriggered(it.achievementId)
                    is RAEvent.OnAchievementProgressUpdated -> onAchievementProgressUpdated(it)
                    is RAEvent.OnLeaderboardAttemptStarted -> onLeaderboardAttemptStarted(it)
                    is RAEvent.OnLeaderboardAttemptUpdated -> onLeaderboardAttemptUpdated(it)
                    is RAEvent.OnLeaderboardAttemptCompleted -> onLeaderboardAttemptCompleted(it)
                    is RAEvent.OnLeaderboardAttemptCancelled -> onLeaderboardAttemptCancelled(it)
                }
            }
        }
    }

    private fun startObservingMainScreenBackground() {
        sessionCoroutineScope.launch {
            combine(_currentLayout, ensureEmulatorIsRunning()) { variant, _ ->
                val layout = variant?.second
                if (layout == null) {
                    RuntimeBackground.None
                } else {
                    loadBackground(layout.mainScreenLayout.backgroundId, layout.mainScreenLayout.backgroundMode)
                }
            }.collect(_mainScreenBackground)
        }
    }

    private fun startObservingSecondaryScreenBackground() {
        sessionCoroutineScope.launch {
            combine(_currentLayout, ensureEmulatorIsRunning()) { variant, _ ->
                val layout = variant?.second
                if (layout == null) {
                    RuntimeBackground.None
                } else {
                    loadBackground(layout.secondaryScreenLayout.backgroundId, layout.secondaryScreenLayout.backgroundMode)
                }
            }.collect(_secondaryScreenBackground)
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
                _runtimeRendererConfiguration.value = RuntimeRendererConfiguration(it.videoFiltering, it.resolutionScaling, it.renderStrategy)
            }
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
        return settingsRepository.observeSelectedLayoutId()
            .flatMapLatest {
                layoutsRepository.observeLayout(it)
                    .onCompletion {
                        emitAll(layoutsRepository.observeLayout(LayoutConfiguration.DEFAULT_ID))
                    }
            }
    }

    private suspend fun getRomInfo(rom: Rom): RomInfo? = withContext(Dispatchers.IO) {
        val fileRomProcessor = romFileProcessorFactory.getFileRomProcessorForDocument(rom.uri)
        fileRomProcessor?.getRomInfo(rom)
    }

    private fun getRomSaveStateSlots(rom: Rom): List<SaveStateSlot> {
        return saveStatesRepository.getRomSaveStates(rom)
    }

    fun isSustainedPerformanceModeEnabled(): Boolean {
        return settingsRepository.isSustainedPerformanceModeEnabled()
    }

    fun getFpsCounterPosition(): FpsCounterPosition {
        return settingsRepository.getFpsCounterPosition()
    }

    private suspend fun getRomEnabledCheats(romInfo: RomInfo): List<Cheat> {
        if (!settingsRepository.areCheatsEnabled() || !emulatorSession.areCheatsEnabled()) {
            return emptyList()
        }

        return cheatsRepository.getRomEnabledCheats(romInfo)
    }

    private suspend fun getRomAchievementData(rom: Rom): GameAchievementData {
        val userAuth = retroAchievementsRepository.getUserAuthentication()
        when (userAuth) {
            is RAUserAuth.Authenticated -> { /* no-op */ }
            is RAUserAuth.AuthenticationExpired -> return GameAchievementData.withDisabledRetroAchievementsIntegration(GameAchievementData.IntegrationStatus.DISABLED_LOGIN_EXPIRED)
            null -> return GameAchievementData.withDisabledRetroAchievementsIntegration(GameAchievementData.IntegrationStatus.DISABLED_NOT_LOGGED_IN)
        }

        return retroAchievementsRepository.getUserGameData(rom.retroAchievementsHash, emulatorSession.isRetroAchievementsHardcoreModeEnabled).fold(
            onSuccess = { userGameData ->
                val gameSummary = retroAchievementsRepository.getGameSummary(rom.retroAchievementsHash)

                if (userGameData != null) {
                    val achievements = userGameData.sets.flatMap { it.achievements }
                    val leaderboards = userGameData.sets.flatMap { it.leaderboards }
                    val hasLeaderboards = leaderboards.isNotEmpty() && emulatorSession.areLeaderboardsEnabled()

                    if (achievements.isEmpty() && !hasLeaderboards) {
                        GameAchievementData.withLimitedRetroAchievementsIntegration(
                            richPresencePatch = gameSummary?.richPresencePatch,
                            icon = gameSummary?.icon,
                        )
                    } else {
                        val lockedAchievements = achievements.filter { !it.isUnlocked }.map { RASimpleAchievement(it.achievement.id, it.achievement.memoryAddress) }
                        val leaderboards = if (hasLeaderboards) {
                            leaderboards.map { RASimpleLeaderboard(it.id, it.mem, it.format) }
                        } else {
                            emptyList()
                        }

                        GameAchievementData.withFullRetroAchievementsIntegration(
                            lockedAchievements = lockedAchievements,
                            leaderboards = leaderboards,
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
        sessionCoroutineScope.launch {
            retroAchievementsRepository.getAchievement(achievementId).onSuccess { achievement ->
                if (achievement != null) {
                    val isHardcoreModeEnabled = emulatorSession.isRetroAchievementsHardcoreModeEnabled
                    retroAchievementsSubmissionHandler.addPendingAchievementSubmission(achievement, isHardcoreModeEnabled)
                }
            }
        }
    }

    private fun onAchievementPrimed(achievementId: Long) {
        if (settingsRepository.areRetroAchievementsActiveChallengeIndicatorsEnabled()) {
            sessionCoroutineScope.launch {
                retroAchievementsRepository.getAchievement(achievementId).onSuccess { achievement ->
                    if (achievement != null) {
                        _achievementsEvent.emit(RAEventUi.AchievementPrimed(achievement))
                    }
                }
            }
        }
    }

    private fun onAchievementUnPrimed(achievementId: Long) {
        sessionCoroutineScope.launch {
            retroAchievementsRepository.getAchievement(achievementId).onSuccess { achievement ->
                if (achievement != null) {
                    _achievementsEvent.emit(RAEventUi.AchievementUnPrimed(achievement))
                }
            }
        }
    }

    private fun onAchievementProgressUpdated(progressEvent: RAEvent.OnAchievementProgressUpdated) {
        if (settingsRepository.areRetroAchievementsProgressIndicatorsEnabled()) {
            sessionCoroutineScope.launch {
                retroAchievementsRepository.getAchievement(progressEvent.achievementId).onSuccess { achievement ->
                    if (achievement != null) {
                        _achievementsEvent.emit(RAEventUi.AchievementProgressUpdated(achievement, progressEvent.current, progressEvent.target, progressEvent.progress))
                    }
                }
            }
        }
    }

    private fun onLeaderboardAttemptStarted(startEvent: RAEvent.OnLeaderboardAttemptStarted) {
        sessionCoroutineScope.launch {
            if (settingsRepository.areRetroAchievementsLeaderboardIndicatorsEnabled()) {
                val leaderboard = retroAchievementsRepository.getLeaderboard(startEvent.leaderboardId)
                if (leaderboard != null) {
                    val setSummary = retroAchievementsRepository.getAchievementSetSummary(leaderboard.setId)
                    if (setSummary != null) {
                        _achievementsEvent.emit(RAEventUi.LeaderboardAttemptStarted(leaderboard, setSummary.iconUrl))
                    }
                }
            }
        }
    }

    private fun onLeaderboardAttemptUpdated(updateEvent: RAEvent.OnLeaderboardAttemptUpdated) {
        sessionCoroutineScope.launch {
            if (settingsRepository.areRetroAchievementsLeaderboardIndicatorsEnabled()) {
                _achievementsEvent.emit(RAEventUi.LeaderboardAttemptUpdated(updateEvent.leaderboardId, updateEvent.formattedValue))
            }
        }
    }

    private fun onLeaderboardAttemptCompleted(completionEvent: RAEvent.OnLeaderboardAttemptCompleted) {
        sessionCoroutineScope.launch {
            retroAchievementsRepository.getLeaderboard(completionEvent.leaderboardId)?.let { leaderboard ->
                retroAchievementsSubmissionHandler.addPendingLeaderboardSubmission(leaderboard, completionEvent.value, completionEvent.formattedValue)
            }
        }
    }

    private fun onLeaderboardAttemptCancelled(cancelEvent: RAEvent.OnLeaderboardAttemptCancelled) {
        sessionCoroutineScope.launch {
            _achievementsEvent.emit(RAEventUi.LeaderboardAttemptCancelled(cancelEvent.leaderboardId))
        }
    }

    private fun startRetroAchievementsSession(rom: Rom) {
        sessionCoroutineScope.launch {
            val achievementData = getRomAchievementData(rom)
            emulatorSession.updateRetroAchievementsIntegrationStatus(achievementData.retroAchievementsIntegrationStatus)
            if (!achievementData.isRetroAchievementsIntegrationEnabled) {
                if (achievementData.retroAchievementsIntegrationStatus == GameAchievementData.IntegrationStatus.DISABLED_LOAD_ERROR) {
                    _raIntegrationEvent.trySend(RAIntegrationEvent.Failed(achievementData.icon))
                } else if (achievementData.retroAchievementsIntegrationStatus == GameAchievementData.IntegrationStatus.DISABLED_LOGIN_EXPIRED) {
                    _raIntegrationEvent.trySend(RAIntegrationEvent.LoginExpired(achievementData.icon))
                }

                return@launch
            }

            raSessionJob = launch {
                // Wait until the emulator has actually started
                ensureEmulatorIsRunning().firstOrNull()

                val isHardcoreModeEnabled = emulatorSession.isRetroAchievementsHardcoreModeEnabled
                val startResult = retroAchievementsRepository.startSession(rom.retroAchievementsHash, isHardcoreModeEnabled)
                if (startResult.isFailure) {
                    if (startResult.exceptionOrNull() is UserTokenExpiredException) {
                        _raIntegrationEvent.trySend(RAIntegrationEvent.LoginExpired(achievementData.icon))
                    } else {
                        _raIntegrationEvent.trySend(RAIntegrationEvent.Failed(achievementData.icon))
                    }
                } else {
                    launch {
                        retroAchievementsSubmissionHandler.startEmulatorSession().collect(_achievementsEvent)
                    }

                    emulatorManager.setupRetroAchievements(achievementData)
                    if (achievementData.hasAchievements) {
                        _raIntegrationEvent.trySend(
                            RAIntegrationEvent.Loaded(
                                icon = achievementData.icon,
                                unlockedAchievements = achievementData.unlockedAchievementCount,
                                totalAchievements = achievementData.totalAchievementCount,
                            )
                        )
                    } else {
                        _raIntegrationEvent.trySend(RAIntegrationEvent.LoadedNoAchievements(achievementData.icon))
                    }

                    delay(30.seconds)
                    while (isActive) {
                        // TODO: Should we pause the session if the app goes to background? If so, how?
                        val richPresenceDescription = MelonEmulator.getRichPresenceStatus()
                        retroAchievementsRepository.sendSessionHeartbeat(rom.retroAchievementsHash, isHardcoreModeEnabled, richPresenceDescription)
                        delay(2.minutes)
                    }
                }
            }
        }
    }

    private fun startTrackingFps() {
        sessionCoroutineScope.launch {
            while (isActive) {
                delay(1.seconds)
                _currentFps.value = emulatorManager.getFps().roundToInt()
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

    private suspend fun startEmulatorSession(
        sessionType: EmulatorSession.SessionType,
        disableHardcore: Boolean = false,
    ) {
        val isUserAuthenticatedInRetroAchievements = retroAchievementsRepository.isUserAuthenticated()
        val isRetroAchievementsHardcoreModeEnabled =
            settingsRepository.isRetroAchievementsHardcoreEnabled() && !disableHardcore
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
                    _achievementsEvent.tryEmit(RAEventUi.Reset)
                    emulatorManager.unloadRetroAchievementsData()
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