package me.magnum.melonds.domain.repositories

import android.net.Uri
import io.reactivex.Observable
import me.magnum.melonds.domain.model.*
import me.magnum.melonds.ui.Theme
import java.util.*

interface SettingsRepository {
    fun getEmulatorConfiguration(): EmulatorConfiguration

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

    fun getVideoFiltering(): VideoFiltering
    fun isThreadedRenderingEnabled(): Boolean
    fun getFpsCounterPosition(): FpsCounterPosition

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
    fun showSoftInput(): Boolean
    fun isTouchHapticFeedbackEnabled(): Boolean
    fun getTouchHapticFeedbackStrength(): Int
    fun getSoftInputOpacity(): Int

    fun areCheatsEnabled(): Boolean

    fun observeTheme(): Observable<Theme>
    fun observeRomIconFiltering(): Observable<RomIconFiltering>
    fun observeRomSearchDirectories(): Observable<Array<Uri>>
    fun observeSelectedLayoutId(): Observable<UUID>

    fun setDsBiosDirectory(directoryUri: Uri)
    fun setDsiBiosDirectory(directoryUri: Uri)
    fun addRomSearchDirectory(directoryUri: Uri)
    fun setControllerConfiguration(controllerConfiguration: ControllerConfiguration)
    fun setRomSortingMode(sortingMode: SortingMode)
    fun setRomSortingOrder(sortingOrder: SortingOrder)
    fun setSelectedLayoutId(layoutId: UUID)
}
