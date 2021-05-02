package me.magnum.melonds.ui.emulator

import android.os.Bundle
import io.reactivex.Completable
import me.magnum.melonds.domain.model.EmulatorConfiguration

abstract class EmulatorDelegate(protected val activity: EmulatorActivity) {
    abstract fun getEmulatorSetupObservable(extras: Bundle?): Completable
    abstract fun getEmulatorConfiguration(): EmulatorConfiguration
    abstract fun getPauseMenuOptions(): List<EmulatorActivity.PauseMenuOption>
    abstract fun onPauseMenuOptionSelected(option: EmulatorActivity.PauseMenuOption)
    abstract fun getCrashContext(): Any
    abstract fun dispose()
}