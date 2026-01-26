package me.magnum.melonds.impl.emulator

/**
 * Event types that can be emitted by the emulator. These constants must match the values defined in AndroidMelonEventMessenger.h
 */
enum class EmulatorEventType(val event: Int) {
    /**
     * Rumble start event. Data:
     * * rumble duration in ms (`i32`)
     */
    EventRumbleStart(100),
    /**
     * Rumble stop event. No data.
     */
    EventRumbleStop(101),
    /**
     * Emulator stop event. No data.
     */
    EventEmulatorStop(102),
}
