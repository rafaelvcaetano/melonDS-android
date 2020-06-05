package me.magnum.melonds.repositories

import io.reactivex.Observable
import me.magnum.melonds.model.ControllerConfiguration
import me.magnum.melonds.model.VideoFiltering
import me.magnum.melonds.ui.Theme

interface SettingsRepository {
    fun getTheme(): Theme

    fun getRomSearchDirectories(): Array<String>

    fun getBiosDirectory(): String?
    fun showBootScreen(): Boolean

    fun getVideoFiltering(): VideoFiltering

    fun saveNextToRomFile(): Boolean
    fun getSaveFileDirectory(): String?

    fun getControllerConfiguration(): ControllerConfiguration
    fun showSoftInput(): Boolean
    fun getSoftInputOpacity(): Int

    fun observeTheme(): Observable<Theme>
    fun observeRomSearchDirectories(): Observable<Array<String>>

    fun setControllerConfiguration(controllerConfiguration: ControllerConfiguration)
}
