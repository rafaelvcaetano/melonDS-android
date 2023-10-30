package me.magnum.melonds.domain.model.emulator

import me.magnum.melonds.MelonEmulator

sealed class FirmwareLaunchResult {
    data class LaunchFailed(val reason: MelonEmulator.FirmwareLoadResult) : FirmwareLaunchResult()
    object LaunchSuccessful : FirmwareLaunchResult()
}