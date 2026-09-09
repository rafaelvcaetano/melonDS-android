package me.magnum.melonds.ui.emulator

internal enum class DeviceSleepTransitionAction {
    IGNORE,
    START,
    KEEP_ACTIVE,
}

internal fun deviceSleepTransitionAction(
    screenOff: Boolean,
    emulatorRunning: Boolean,
    transitionActive: Boolean,
): DeviceSleepTransitionAction {
    if (!screenOff || !emulatorRunning) {
        return DeviceSleepTransitionAction.IGNORE
    }
    return if (transitionActive) {
        DeviceSleepTransitionAction.KEEP_ACTIVE
    } else {
        DeviceSleepTransitionAction.START
    }
}
