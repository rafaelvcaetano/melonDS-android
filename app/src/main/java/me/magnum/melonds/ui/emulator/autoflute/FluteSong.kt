package me.magnum.melonds.ui.emulator.autoflute

/**
 * A single stylus event of a recorded flute performance. [timeMs] is relative to the first event of the song.
 * Coordinates are in DS touch screen space (0..255, 0..191).
 */
data class FluteEvent(
    val timeMs: Long,
    val type: Type,
    val x: Int,
    val y: Int,
) {
    enum class Type {
        DOWN,
        MOVE,
        UP,
    }
}

data class FluteSong(
    val id: String,
    val name: String,
    val events: List<FluteEvent>,
) {
    val durationMs: Long
        get() = events.lastOrNull()?.timeMs ?: 0L
}
