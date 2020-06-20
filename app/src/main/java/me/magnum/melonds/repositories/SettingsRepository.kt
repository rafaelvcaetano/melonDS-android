package me.magnum.melonds.repositories

import io.reactivex.Observable
import me.magnum.melonds.model.*
import me.magnum.melonds.ui.Theme

interface SettingsRepository {
    fun getTheme(): Theme

    fun getRomSearchDirectories(): Array<String>

    fun getBiosDirectory(): String?
    fun showBootScreen(): Boolean

    fun getVideoFiltering(): VideoFiltering

    fun getRomSortingMode(): SortingMode
    fun getRomSortingOrder(): SortingOrder
    fun saveNextToRomFile(): Boolean
    fun getSaveFileDirectory(): String?
    fun getSaveStateDirectory(rom: Rom): String

    fun getControllerConfiguration(): ControllerConfiguration
    fun showSoftInput(): Boolean
    fun getSoftInputOpacity(): Int

    fun observeTheme(): Observable<Theme>
    fun observeRomSearchDirectories(): Observable<Array<String>>

    fun setControllerConfiguration(controllerConfiguration: ControllerConfiguration)
    fun setRomSortingMode(sortingMode: SortingMode)
    fun setRomSortingOrder(sortingOrder: SortingOrder)
}
