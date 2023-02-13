package me.magnum.melonds.ui.emulator

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.rxMaybe
import me.magnum.melonds.common.Schedulers
import me.magnum.melonds.common.romprocessors.RomFileProcessorFactory
import me.magnum.melonds.common.uridelegates.UriHandler
import me.magnum.melonds.domain.model.*
import me.magnum.melonds.domain.model.retroachievements.RASimpleAchievement
import me.magnum.melonds.domain.repositories.*
import me.magnum.melonds.extensions.addTo
import me.magnum.melonds.ui.emulator.exceptions.RomLoadException
import me.magnum.melonds.ui.emulator.exceptions.SramLoadException
import me.magnum.melonds.ui.emulator.firmware.FirmwarePauseMenuOption
import me.magnum.melonds.ui.emulator.rom.RomPauseMenuOption
import me.magnum.rcheevosapi.model.RAAchievement
import java.util.*
import javax.inject.Inject

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
        private val uriHandler: UriHandler,
        private val schedulers: Schedulers
) : ViewModel() {

    private val disposables = CompositeDisposable()
    private var layoutLoadDisposable: Disposable? = null
    private var backgroundLoadDisposable: Disposable? = null
    private val layoutLiveData = MutableLiveData<LayoutConfiguration>()
    private val backgroundLiveData = MutableLiveData<RuntimeBackground>()
    private val _achievementTriggeredEvent = MutableSharedFlow<RAAchievement>(extraBufferCapacity = 5, onBufferOverflow = BufferOverflow.SUSPEND)
    val achievementTriggeredEvent = _achievementTriggeredEvent.asSharedFlow()

    private var currentSystemOrientation: Orientation? = null

    fun setSystemOrientation(orientation: Orientation) {
        if (orientation != currentSystemOrientation) {
            currentSystemOrientation = orientation
            loadBackgroundForCurrentLayout()
        }
    }

    fun getLayout(): LiveData<LayoutConfiguration> {
        return layoutLiveData
    }

    fun loadLayoutForRom(rom: Rom) {
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

        layoutLoadDisposable?.dispose()
        layoutLoadDisposable = layoutObservable.subscribeOn(schedulers.backgroundThreadScheduler)
                .observeOn(schedulers.uiThreadScheduler)
                .subscribe {
                    layoutLiveData.value = it
                    loadBackgroundForCurrentLayout()
                }
    }

    fun loadLayoutForFirmware() {
        layoutLoadDisposable?.dispose()
        layoutLoadDisposable = getGlobalLayoutObservable()
                .subscribeOn(schedulers.backgroundThreadScheduler)
                .observeOn(schedulers.uiThreadScheduler)
                .subscribe {
                    layoutLiveData.value = it
                    loadBackgroundForCurrentLayout()
                }
    }

    fun getBackground(): LiveData<RuntimeBackground> {
        return backgroundLiveData
    }

    private fun loadBackgroundForCurrentLayout() {
        layoutLiveData.value?.let { layout ->
            currentSystemOrientation?.let { orientation ->
                if (orientation == Orientation.PORTRAIT) {
                    loadBackground(layout.portraitLayout.backgroundId, layout.portraitLayout.backgroundMode)
                } else {
                    loadBackground(layout.landscapeLayout.backgroundId, layout.landscapeLayout.backgroundMode)
                }
            }
        }
    }

    private fun loadBackground(backgroundId: UUID?, mode: BackgroundMode) {
        backgroundLoadDisposable?.dispose()
        if (backgroundId == null) {
            backgroundLiveData.value = RuntimeBackground(null, mode)
        } else {
            backgroundLoadDisposable = backgroundsRepository.getBackground(backgroundId)
                    .subscribeOn(schedulers.backgroundThreadScheduler)
                    .materialize()
                    .subscribe { message ->
                        backgroundLiveData.postValue(RuntimeBackground(message.value, mode))
                    }
        }
    }

    fun getSoftInputOpacity(): Int {
        return layoutLiveData.value?.let {
            if (it.useCustomOpacity) {
                it.opacity
            } else {
                settingsRepository.getSoftInputOpacity()
            }
        } ?: settingsRepository.getSoftInputOpacity()
    }

    fun getRomSearchDirectory(): Uri? {
        return settingsRepository.getRomSearchDirectories().firstOrNull()
    }

    fun getDsBiosDirectory(): Uri? {
        return settingsRepository.getDsBiosDirectory()
    }

    fun getDsiBiosDirectory(): Uri? {
        return settingsRepository.getDsiBiosDirectory()
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

    fun isTouchHapticFeedbackEnabled(): Boolean {
        return settingsRepository.isTouchHapticFeedbackEnabled()
    }

    fun isRewindEnabled(): Boolean {
        return settingsRepository.isRewindEnabled()
    }

    fun getRomLoader(rom: Rom): Single<Pair<Rom, Uri>> {
        val fileRomProcessor = romFileProcessorFactory.getFileRomProcessorForDocument(rom.uri)
        // ?.flatMap { WfcRomPatcher().patchRom(rom, it).andThen(Single.just(it)) }
        return fileRomProcessor?.getRealRomUri(rom)?.map { rom to it } ?: Single.error(RomLoadException("Unsupported ROM file extension"))
    }

    fun getRomInfo(rom: Rom): RomInfo? {
        val fileRomProcessor = romFileProcessorFactory.getFileRomProcessorForDocument(rom.uri)
        return fileRomProcessor?.getRomInfo(rom)
    }

    fun getRomSramFile(rom: Rom): Uri {
        val rootDirUri = settingsRepository.getSaveFileDirectory(rom)

        val rootDocument = uriHandler.getUriTreeDocument(rootDirUri) ?: throw SramLoadException("Cannot create root document: $rootDirUri")
        val romDocument = uriHandler.getUriDocument(rom.uri)

        val romFileName = romDocument?.name ?: throw SramLoadException("Cannot determine SRAM file name: ${romDocument?.uri}")
        val sramFileName = romFileName.replaceAfterLast('.', "sav", "$romFileName.sav")

        val sramDocument = rootDocument.findFile(sramFileName)
        return sramDocument?.uri ?: rootDocument.createFile("*/*", sramFileName)?.uri ?: throw SramLoadException("Could not create temporary SRAM file at ${rootDocument.uri}")
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

    fun getEmulatorConfigurationForRom(rom: Rom): EmulatorConfiguration {
        val baseConfiguration = settingsRepository.getEmulatorConfiguration()
        val mustUseCustomBios = baseConfiguration.useCustomBios || rom.config.runtimeConsoleType != RuntimeConsoleType.DEFAULT
        return baseConfiguration.copy(
                useCustomBios = mustUseCustomBios,
                showBootScreen = baseConfiguration.showBootScreen && mustUseCustomBios,
                consoleType = getRomOptionOrDefault(rom.config.runtimeConsoleType, baseConfiguration.consoleType),
                micSource = getRomOptionOrDefault(rom.config.runtimeMicSource, baseConfiguration.micSource)
        )
    }

    fun getEmulatorConfigurationForFirmware(consoleType: ConsoleType): EmulatorConfiguration {
        return settingsRepository.getEmulatorConfiguration().copy(
                useCustomBios = true, // Running a firmware requires a custom BIOS
                consoleType = consoleType
        )
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

    private fun <T, U> getRomOptionOrDefault(romOption: T, default: U): U where T : RuntimeEnum<T, U> {
        return if (romOption.getDefault() == romOption)
            default
        else
            romOption.getValue()
    }

    fun getRomEnabledCheats(romInfo: RomInfo): LiveData<List<Cheat>> {
        val liveData = MutableLiveData<List<Cheat>>()

        if (!settingsRepository.areCheatsEnabled()) {
            liveData.value = emptyList()
        } else {
            cheatsRepository.getRomEnabledCheats(romInfo).subscribe { cheats ->
                liveData.postValue(cheats)
            }.addTo(disposables)
        }

        return liveData
    }

    fun getRomAchievements(rom: Rom): LiveData<List<RASimpleAchievement>> {
        val liveData = MutableLiveData<List<RASimpleAchievement>>()

        viewModelScope.launch {
            if (retroAchievementsRepository.isUserAuthenticated()) {
                val achievements = retroAchievementsRepository.getGameUserAchievements(rom.retroAchievementsHash).map { achievements ->
                    achievements.map { RASimpleAchievement(it.achievement.id, it.achievement.memoryAddress) }
                }
                liveData.value = achievements.getOrElse { emptyList() }
            } else {
                liveData.value = emptyList()
            }
        }

        return liveData
    }

    fun onAchievementTriggered(achievementId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            retroAchievementsRepository.getAchievement(achievementId)
                .onSuccess {
                    if (it != null) {
                        _achievementTriggeredEvent.emit(it)
                        retroAchievementsRepository.awardAchievement(it)
                    }
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
        disposables.clear()
        layoutLoadDisposable?.dispose()
        backgroundLoadDisposable?.dispose()
    }
}