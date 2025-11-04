package me.magnum.melonds.domain.repositories

import android.net.Uri
import io.reactivex.Observable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import me.magnum.melonds.domain.model.*
import me.magnum.melonds.domain.model.camera.DSiCameraSourceType
import me.magnum.melonds.domain.model.input.SoftInputBehaviour
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
    fun observeExternalDisplayScreen(): Flow<DsExternalScreen>
    fun isExternalDisplayKeepAspectRationEnabled(): Boolean
    fun observeExternalDisplayKeepAspectRationEnabled(): Flow<Boolean>
    fun isExternalDisplayRotateLeftEnabled(): Flow<Boolean>
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
    fun observeControllerConfiguration(): StateFlow<ControllerConfiguration>
    fun getSelectedLayoutId(): UUID
    fun getSoftInputBehaviour(): Flow<SoftInputBehaviour>
    fun getExternalLayoutId(): UUID
    fun isTouchHapticFeedbackEnabled(): Flow<Boolean>
    fun getTouchHapticFeedbackStrength(): Int
    fun getSoftInputOpacity(): Flow<Int>

    fun isRetroAchievementsRichPresenceEnabled(): Boolean
    fun isRetroAchievementsHardcoreEnabled(): Boolean

    fun areCheatsEnabled(): Boolean

    fun observeTheme(): Observable<Theme>
    fun observeRomIconFiltering(): Flow<RomIconFiltering>
    fun observeRomSearchDirectories(): Flow<Array<Uri>>
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
    fun setExternalDisplayRotateLeftEnabled(enabled: Boolean)

    fun observeRenderConfiguration(): Flow<RendererConfiguration>
}
