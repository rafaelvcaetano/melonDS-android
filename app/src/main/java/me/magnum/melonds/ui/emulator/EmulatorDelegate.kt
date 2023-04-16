package me.magnum.melonds.ui.emulator

import android.os.Bundle

abstract class EmulatorDelegate(protected val activity: EmulatorActivity) {
    abstract fun getEmulatorSetupObservable(extras: Bundle?)
    abstract fun getPauseMenuOptions(): List<PauseMenuOption>
    abstract fun onPauseMenuOptionSelected(option: PauseMenuOption)
    abstract fun performQuickSave()
    abstract fun performQuickLoad()
    abstract fun dispose()
}