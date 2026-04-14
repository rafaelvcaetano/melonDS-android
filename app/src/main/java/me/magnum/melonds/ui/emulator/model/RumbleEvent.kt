package me.magnum.melonds.ui.emulator.model

sealed class RumbleEvent {
    data class RumbleStart(val duration: Int) : RumbleEvent()
    data object RumbleStop : RumbleEvent()
}