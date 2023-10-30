package me.magnum.melonds.ui.emulator.model

sealed class ToastEvent {
    data object GbaLoadFailed : ToastEvent()
    data object ResetFailed : ToastEvent()
    data object RewindNotEnabled : ToastEvent()
    data object RewindNotAvailableWhileRAHardcoreModeEnabled : ToastEvent()
    data object StateSaveFailed : ToastEvent()
    data object StateLoadFailed : ToastEvent()
    data object StateStateDoesNotExist : ToastEvent()
    data object QuickSaveSuccessful : ToastEvent()
    data object QuickLoadSuccessful : ToastEvent()
    data object CannotUseSaveStatesWhenRAHardcoreIsEnabled : ToastEvent()
    data object CannotSaveStateWhenRunningFirmware : ToastEvent()
    data object CannotLoadStateWhenRunningFirmware : ToastEvent()
    data object CannotSwitchRetroAchievementsMode : ToastEvent()
}