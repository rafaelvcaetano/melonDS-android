package me.magnum.melonds.ui.emulator.autoflute

import kotlin.math.abs

/**
 * A spot on the touch screen and the pitch the flute produces there.
 */
data class FluteTouchPoint(
    val x: Int,
    val y: Int,
    val semitone: Double,
)

/**
 * Knows which touch screen position produces which pitch. This is learnt by having the player touch every pipe of
 * the flute once while the game's audio is recorded.
 */
class FluteCalibration(val points: List<FluteTouchPoint>) {

    companion object {
        // Pitches closer than this are considered to be the same pipe
        private const val SAME_PIPE_TOLERANCE = 0.5

        // Window in which a note in the audio is matched to a stylus touch. Covers audio output latency
        private const val MATCH_SLACK_MS = 150L

        private const val MIN_PIPES = 2

        /**
         * Builds a calibration from a performance in which each pipe was touched at least once.
         *
         * @param events the stylus events of the performance
         * @param eventsOffsetMs time of the first stylus event on the audio capture's timeline
         * @param notes the notes detected in the audio capture
         * @return the calibration, or null if fewer than two distinct pipes could be identified
         */
        fun fromPerformance(events: List<FluteEvent>, eventsOffsetMs: Long, notes: List<DetectedNote>): FluteCalibration? {
            val pairs = mutableListOf<FluteTouchPoint>()

            for (touch in splitTouches(events)) {
                val start = touch.startMs + eventsOffsetMs
                val end = touch.endMs + eventsOffsetMs

                val note = notes.maxByOrNull { overlap(start - MATCH_SLACK_MS, end + MATCH_SLACK_MS, it.startMs, it.endMs) }
                if (note == null || overlap(start - MATCH_SLACK_MS, end + MATCH_SLACK_MS, note.startMs, note.endMs) <= 0) continue

                pairs.add(FluteTouchPoint(median(touch.xs), median(touch.ys), note.semitone))
            }

            // Several touches of the same pipe are merged into one
            val merged = mutableListOf<MutableList<FluteTouchPoint>>()
            for (pair in pairs.sortedBy { it.semitone }) {
                val group = merged.lastOrNull()
                if (group != null && abs(pair.semitone - group.last().semitone) <= SAME_PIPE_TOLERANCE) {
                    group.add(pair)
                } else {
                    merged.add(mutableListOf(pair))
                }
            }

            val points = merged.map { group ->
                FluteTouchPoint(
                    x = group.map { it.x }.average().toInt(),
                    y = group.map { it.y }.average().toInt(),
                    semitone = group.map { it.semitone }.average(),
                )
            }

            return if (points.size >= MIN_PIPES) FluteCalibration(points) else null
        }

        private class Touch(val startMs: Long, var endMs: Long, val xs: MutableList<Int>, val ys: MutableList<Int>)

        private fun splitTouches(events: List<FluteEvent>): List<Touch> {
            val touches = mutableListOf<Touch>()
            var current: Touch? = null
            for (event in events) {
                when (event.type) {
                    FluteEvent.Type.DOWN -> {
                        current = Touch(event.timeMs, event.timeMs, mutableListOf(event.x), mutableListOf(event.y))
                    }
                    FluteEvent.Type.MOVE -> current?.let {
                        it.endMs = event.timeMs
                        it.xs.add(event.x)
                        it.ys.add(event.y)
                    }
                    FluteEvent.Type.UP -> current?.let {
                        it.endMs = event.timeMs
                        touches.add(it)
                        current = null
                    }
                }
            }
            return touches
        }

        private fun overlap(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long): Long {
            return maxOf(0L, minOf(aEnd, bEnd) - maxOf(aStart, bStart))
        }

        private fun median(values: List<Int>): Int = values.sorted()[values.size / 2]
    }

    /**
     * Finds the touch point whose pitch matches [semitone] best. The game's characters may play in a different octave
     * than the flute, so pitches are compared by note name first and by octave distance second.
     */
    fun closestPoint(semitone: Double): FluteTouchPoint {
        return points.minWith(
            compareBy<FluteTouchPoint> { roundedDistance(pitchClassDistance(it.semitone, semitone)) }
                .thenBy { abs(it.semitone - semitone) }
        )
    }

    private fun pitchClassDistance(a: Double, b: Double): Double {
        val diff = ((a - b) % 12.0 + 12.0) % 12.0
        return minOf(diff, 12.0 - diff)
    }

    // Avoids picking a pipe over another because of tiny tuning differences
    private fun roundedDistance(distance: Double): Double = Math.round(distance * 2) / 2.0
}

/**
 * Turns notes detected in audio into stylus events that play them on the flute.
 */
object FluteSongBuilder {
    // The game needs to see the stylus lift between two notes to start a new note
    private const val MIN_RELEASE_GAP_MS = 40L

    fun build(notes: List<DetectedNote>, calibration: FluteCalibration): List<FluteEvent> {
        if (notes.isEmpty()) return emptyList()

        val origin = notes.first().startMs
        val events = mutableListOf<FluteEvent>()
        notes.forEachIndexed { index, note ->
            val point = calibration.closestPoint(note.semitone)
            val start = note.startMs - origin
            var end = note.endMs - origin
            val nextStart = notes.getOrNull(index + 1)?.let { it.startMs - origin }
            if (nextStart != null && end > nextStart - MIN_RELEASE_GAP_MS) {
                end = maxOf(start + 1, nextStart - MIN_RELEASE_GAP_MS)
            }

            events.add(FluteEvent(start, FluteEvent.Type.DOWN, point.x, point.y))
            events.add(FluteEvent(end, FluteEvent.Type.UP, point.x, point.y))
        }
        return events
    }
}
