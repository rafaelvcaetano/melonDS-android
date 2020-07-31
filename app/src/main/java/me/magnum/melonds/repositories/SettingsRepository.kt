package me.magnum.melonds.repositories

import android.net.Uri
import io.reactivex.Observable
import me.magnum.melonds.model.*
import me.magnum.melonds.ui.Theme

interface SettingsRepository {
    fun getTheme(): Theme

    fun getRomSearchDirectories(): Array<Uri>

    fun getBiosDirectory(): Uri?
    fun showBootScreen(): Boolean

    fun getVideoFiltering(): VideoFiltering

    fun getRomSortingMode(): SortingMode
    fun getRomSortingOrder(): SortingOrder
    fun saveNextToRomFile(): Boolean
    fun getSaveFileDirectory(): Uri?
    fun getSaveStateDirectory(rom: Rom): String

    fun getControllerConfiguration(): ControllerConfiguration
    fun showSoftInput(): Boolean
    fun getSoftInputOpacity(): Int

    fun observeTheme(): Observable<Theme>
    fun observeRomSearchDirectories(): Observable<Array<Uri>>

    fun setBiosDirectory(directoryUri: Uri)
    fun setControllerConfiguration(controllerConfiguration: ControllerConfiguration)
    fun setRomSortingMode(sortingMode: SortingMode)
    fun setRomSortingOrder(sortingOrder: SortingOrder)
}
