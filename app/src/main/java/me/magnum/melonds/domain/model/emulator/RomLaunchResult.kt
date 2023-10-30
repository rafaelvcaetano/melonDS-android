package me.magnum.melonds.domain.model.emulator

import me.magnum.melonds.MelonEmulator

sealed class RomLaunchResult {
    data class LaunchFailedSramProblem(val reason: Exception) : RomLaunchResult()
    data class LaunchFailed(val reason: MelonEmulator.LoadResult) : RomLaunchResult()
    data class LaunchSuccessful(val isGbaLoadSuccessful: Boolean) : RomLaunchResult()
}