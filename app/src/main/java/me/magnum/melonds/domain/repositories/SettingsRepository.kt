package me.magnum.melonds.domain.repositories

import android.net.Uri
import io.reactivex.Observable
import kotlinx.coroutines.flow.Flow
import me.magnum.melonds.domain.model.*
import me.magnum.melonds.domain.model.camera.DSiCameraSourceType
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.ui.Theme
import java.util.*

interface SettingsRepository {
    suspend fun getEmulatorConfiguration(): EmulatorConfiguration

    fun getTheme(): Theme
    fun getFastForwardSpeedMultiplier(): Float
    fun isRewindEnabled(): Boolean
    fun isSustainedPerformanceModeEnabled(): Boolean

    fun getRomSearchDirectories(): Array<Uri>
    fun clearRomSearchDirectories()
    fun getRomIconFiltering(): RomIconFiltering
    fun getRomCacheMaxSize(): SizeUnit
    fun showRomFileName(): Boolean

    fun getDefaultConsoleType(): ConsoleType
    fun getFirmwareConfiguration(): FirmwareConfiguration
    fun useCustomBios(): Boolean
    fun getDsBiosDirectory(): Uri?
    fun getDsiBiosDirectory(): Uri?
    fun showBootScreen(): Boolean
    fun isJitEnabled(): Boolean

    fun getVideoRenderer(): Flow<VideoRenderer>
    fun getVideoInternalResolutionScaling(): Flow<Int>
    fun getVideoFiltering(): Flow<VideoFiltering>
    fun isThreadedRenderingEnabled(): Flow<Boolean>
    fun getFpsCounterPosition(): FpsCounterPosition
    fun getExternalDisplayScreen(): DsExternalScreen
    fun isExternalDisplayKeepAspectRatioEnabled(): Boolean
    fun getDSiCameraSource(): DSiCameraSourceType
    fun getDSiCameraStaticImage(): Uri?

    fun isSoundEnabled(): Boolean
    fun getAudioLatency(): AudioLatency
    fun getMicSource(): MicSource

    fun getRomSortingMode(): SortingMode
    fun getRomSortingOrder(): SortingOrder
    fun saveNextToRomFile(): Boolean
    fun getSaveFileDirectory(): Uri?
    fun getSaveFileDirectory(rom: Rom): Uri
    fun getSaveStateLocation(rom: Rom): SaveStateLocation
    fun getSaveStateDirectory(rom: Rom): Uri?

    fun getControllerConfiguration(): ControllerConfiguration
    fun getSelectedLayoutId(): UUID
    fun showSoftInput(): Flow<Boolean>
    fun getExternalLayoutId(): UUID
    fun isTouchHapticFeedbackEnabled(): Flow<Boolean>
    fun getTouchHapticFeedbackStrength(): Int
    fun getSoftInputOpacity(): Flow<Int>

    fun isRetroAchievementsRichPresenceEnabled(): Boolean
    fun isRetroAchievementsHardcoreEnabled(): Boolean

    fun areCheatsEnabled(): Boolean

    fun observeTheme(): Observable<Theme>
    fun observeRomIconFiltering(): Flow<RomIconFiltering>
    fun observeShowRomFileName(): Flow<Boolean>
    fun observeRomSearchDirectories(): Observable<Array<Uri>>
    fun observeSelectedLayoutId(): Observable<UUID>
    fun observeExternalLayoutId(): Observable<UUID>
    fun observeDSiCameraSource(): Flow<DSiCameraSourceType>
    fun observeDSiCameraStaticImage(): Flow<Uri?>

    fun setDsBiosDirectory(directoryUri: Uri)
    fun setDsiBiosDirectory(directoryUri: Uri)
    fun addRomSearchDirectory(directoryUri: Uri)
    fun setControllerConfiguration(controllerConfiguration: ControllerConfiguration)
    fun setRomSortingMode(sortingMode: SortingMode)
    fun setRomSortingOrder(sortingOrder: SortingOrder)
    fun setSelectedLayoutId(layoutId: UUID)
    fun setExternalLayoutId(layoutId: UUID)
    fun setExternalDisplayScreen(screen: DsExternalScreen)
    fun setExternalDisplayKeepAspectRatioEnabled(enabled: Boolean)

    fun observeRenderConfiguration(): Flow<RendererConfiguration>
}
