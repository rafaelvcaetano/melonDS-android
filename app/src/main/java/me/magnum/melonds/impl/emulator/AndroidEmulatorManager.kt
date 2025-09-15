package me.magnum.melonds.impl.emulator

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.withContext
import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.common.PermissionHandler
import me.magnum.melonds.common.RetroAchievementsCallback
import me.magnum.melonds.common.romprocessors.RomFileProcessorFactory
import me.magnum.melonds.common.runtime.ScreenshotFrameBufferProvider
import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.EmulatorConfiguration
import me.magnum.melonds.domain.model.MicSource
import me.magnum.melonds.domain.model.emulator.FirmwareLaunchResult
import me.magnum.melonds.domain.model.emulator.RomLaunchResult
import me.magnum.melonds.domain.model.retroachievements.GameAchievementData
import me.magnum.melonds.domain.model.retroachievements.RAEvent
import me.magnum.melonds.domain.model.retroachievements.RASimpleAchievement
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.domain.model.rom.config.RomGbaSlotConfig
import me.magnum.melonds.domain.model.rom.config.RuntimeConsoleType
import me.magnum.melonds.domain.model.rom.config.RuntimeEnum
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.domain.services.EmulatorManager
import me.magnum.melonds.extensions.extension
import me.magnum.melonds.impl.camera.DSiCameraSourceMultiplexer
import me.magnum.melonds.ui.emulator.exceptions.RomLoadException
import me.magnum.melonds.ui.emulator.rewind.model.RewindSaveState
import me.magnum.melonds.ui.emulator.rewind.model.RewindWindow

class AndroidEmulatorManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val sramProvider: SramProvider,
    private val screenshotFrameBufferProvider: ScreenshotFrameBufferProvider,
    private val romFileProcessorFactory: RomFileProcessorFactory,
    private val permissionHandler: PermissionHandler,
    private val cameraManager: DSiCameraSourceMultiplexer,
) : EmulatorManager {

    private val achievementsSharedFlow = MutableSharedFlow<RAEvent>(replay = 0, extraBufferCapacity = Int.MAX_VALUE)

    private val loadedAchievements = mutableListOf<RASimpleAchievement>()

    override suspend fun loadRom(rom: Rom, cheats: List<Cheat>, glContext: Long): RomLaunchResult {
        return withContext(Dispatchers.IO) {
            val fileRomDocument = DocumentFile.fromSingleUri(context, rom.uri) ?: return@withContext RomLaunchResult.LaunchFailedRomNotFound
            val fileRomProcessor = romFileProcessorFactory.getFileRomProcessorForDocument(fileRomDocument)
            val romUri = fileRomProcessor?.getRealRomUri(rom)?.await() ?: throw RomLoadException("Unsupported ROM file extension: ${fileRomDocument.extension}")

            setupEmulator(getRomEmulatorConfiguration(rom), glContext)

            val sram = try {
                sramProvider.getSramForRom(rom)
            } catch (exception: SramLoadException) {
                return@withContext RomLaunchResult.LaunchFailedSramProblem(exception)
            }

            val gbaSlotRomConfig = rom.config.gbaSlotConfig
            val gbaSlotType = when (gbaSlotRomConfig) {
                RomGbaSlotConfig.None -> MelonEmulator.GbaSlotType.NONE
                is RomGbaSlotConfig.GbaRom -> MelonEmulator.GbaSlotType.GBA_ROM
                RomGbaSlotConfig.MemoryExpansion -> MelonEmulator.GbaSlotType.MEMORY_EXPANSION
            }

            val loadResult = MelonEmulator.loadRom(
                romUri = romUri,
                sramUri = sram,
                gbaSlotType = gbaSlotType,
                gbaRomUri = (gbaSlotRomConfig as? RomGbaSlotConfig.GbaRom)?.romPath,
                gbaSramUri = (gbaSlotRomConfig as? RomGbaSlotConfig.GbaRom)?.savePath
            )
            if (loadResult.isTerminal || !isActive) {
                cameraManager.stopCurrentCameraSource()
                MelonEmulator.stopEmulation()
                RomLaunchResult.LaunchFailed(loadResult)
            } else {
                MelonEmulator.setupCheats(cheats.toTypedArray())
                MelonEmulator.startEmulation()

                RomLaunchResult.LaunchSuccessful(loadResult != MelonEmulator.LoadResult.SUCCESS_GBA_FAILED)
            }
        }
    }

    override suspend fun loadFirmware(consoleType: ConsoleType, glContext: Long): FirmwareLaunchResult {
        return withContext(Dispatchers.IO) {
            setupEmulator(getFirmwareEmulatorConfiguration(consoleType), glContext)
            val result = MelonEmulator.bootFirmware()
            if (result != MelonEmulator.FirmwareLoadResult.SUCCESS) {
                cameraManager.stopCurrentCameraSource()
                MelonEmulator.stopEmulation()
                FirmwareLaunchResult.LaunchFailed(result)
            } else {
                MelonEmulator.startEmulation()
                FirmwareLaunchResult.LaunchSuccessful
            }
        }
    }

    override suspend fun updateRomEmulatorConfiguration(rom: Rom) {
        val configuration = getRomEmulatorConfiguration(rom)
        MelonEmulator.updateEmulatorConfiguration(configuration)
    }

    override suspend fun updateFirmwareEmulatorConfiguration(consoleType: ConsoleType) {
        val configuration = getFirmwareEmulatorConfiguration(consoleType)
        MelonEmulator.updateEmulatorConfiguration(configuration)
    }

    override suspend fun getRewindWindow(): RewindWindow {
        return MelonEmulator.getRewindWindow()
    }

    override fun getFps(): Int {
        return MelonEmulator.getFPS()
    }

    override suspend fun pauseEmulator() {
        MelonEmulator.pauseEmulation()
    }

    override suspend fun resumeEmulator() {
        MelonEmulator.resumeEmulation()
    }

    override suspend fun resetEmulator() {
        MelonEmulator.resetEmulation()
    }

    override suspend fun updateCheats(cheats: List<Cheat>) {
        MelonEmulator.setupCheats(cheats.toTypedArray())
    }

    override suspend fun setupAchievements(achievementData: GameAchievementData) {
        val richPresencePath = if (settingsRepository.isRetroAchievementsRichPresenceEnabled()) {
            achievementData.richPresencePatch
        } else {
            null
        }

        loadedAchievements.addAll(achievementData.lockedAchievements)
        MelonEmulator.setupAchievements(achievementData.lockedAchievements.toTypedArray(), richPresencePath)
    }

    override fun unloadAchievements() {
        if (loadedAchievements.isNotEmpty()) {
            MelonEmulator.unloadAchievements(loadedAchievements.toTypedArray())
            loadedAchievements.clear()
        }
    }

    override suspend fun loadRewindState(rewindSaveState: RewindSaveState): Boolean {
        return MelonEmulator.loadRewindState(rewindSaveState)
    }

    override suspend fun saveState(saveStateFileUri: Uri): Boolean {
        return MelonEmulator.saveState(saveStateFileUri)
    }

    override suspend fun loadState(saveStateFileUri: Uri): Boolean {
        return MelonEmulator.loadState(saveStateFileUri)
    }

    override fun stopEmulator() {
        MelonEmulator.stopEmulation()
        cameraManager.stopCurrentCameraSource()
        loadedAchievements.clear()
    }

    override fun cleanEmulator() {
        cameraManager.dispose()
    }

    override fun observeRetroAchievementEvents(): Flow<RAEvent> {
        return achievementsSharedFlow.asSharedFlow()
    }

    private fun setupEmulator(emulatorConfiguration: EmulatorConfiguration, glContext: Long) {
        MelonEmulator.setupEmulator(
            emulatorConfiguration = emulatorConfiguration,
            assetManager = context.assets,
            dsiCameraSource = cameraManager,
            retroAchievementsCallback = object : RetroAchievementsCallback {
                override fun onAchievementPrimed(achievementId: Long) {
                    achievementsSharedFlow.tryEmit(RAEvent.OnAchievementPrimed(achievementId))
                }

                override fun onAchievementTriggered(achievementId: Long) {
                    achievementsSharedFlow.tryEmit(RAEvent.OnAchievementTriggered(achievementId))
                }

                override fun onAchievementUnprimed(achievementId: Long) {
                    achievementsSharedFlow.tryEmit(RAEvent.OnAchievementUnPrimed(achievementId))
                }
            },
            screenshotBuffer = screenshotFrameBufferProvider.frameBuffer(),
            glContext = glContext,
        )
    }

    private suspend fun getRomEmulatorConfiguration(rom: Rom): EmulatorConfiguration {
        val baseConfiguration = settingsRepository.getEmulatorConfiguration()
        val mustUseCustomBios = baseConfiguration.useCustomBios || rom.config.runtimeConsoleType != RuntimeConsoleType.DEFAULT
        return baseConfiguration.copy(
            useCustomBios = mustUseCustomBios,
            showBootScreen = baseConfiguration.showBootScreen && mustUseCustomBios,
            consoleType = getRomOptionOrDefault(rom.config.runtimeConsoleType, baseConfiguration.consoleType),
            micSource = getRomOptionOrDefault(rom.config.runtimeMicSource, baseConfiguration.micSource)
        ).run { getPermissionAdjustedConfiguration(this) }
    }

    private suspend fun getFirmwareEmulatorConfiguration(consoleType: ConsoleType): EmulatorConfiguration {
        return settingsRepository.getEmulatorConfiguration().copy(
            consoleType = consoleType,
            useCustomBios = true,
            showBootScreen = true,
        ).run { getPermissionAdjustedConfiguration(this) }
    }

    private fun <T, U> getRomOptionOrDefault(romOption: T, default: U): U where T : RuntimeEnum<T, U> {
        return if (romOption.getDefault() == romOption) {
            default
        } else {
            romOption.getValue()
        }
    }

    private suspend fun getPermissionAdjustedConfiguration(originalConfiguration: EmulatorConfiguration): EmulatorConfiguration {
        if (originalConfiguration.micSource == MicSource.DEVICE) {
            if (!permissionHandler.checkPermission(android.Manifest.permission.RECORD_AUDIO)) {
                return originalConfiguration.copy(micSource = MicSource.NONE)
            }
        }

        return originalConfiguration
    }
}