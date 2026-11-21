package me.magnum.melonds.ui.emulator.autoflute

import kotlin.math.abs
import kotlin.math.ln

/**
 * A single note found in an audio recording. [semitone] is the pitch in semitones relative to A4 (440 Hz).
 */
data class DetectedNote(
    val startMs: Long,
    val endMs: Long,
    val semitone: Double,
) {
    val durationMs: Long get() = endMs - startMs
}

/**
 * Monophonic pitch tracker (YIN algorithm) that turns a recording of someone playing a melody into a list of notes.
 */
object PitchAnalyzer {
    private const val DECIMATION = 4
    private const val WINDOW = 512
    private const val HOP = 240
    private const val MIN_TAU = 6
    private const val MAX_TAU = 60
    private const val YIN_THRESHOLD = 0.15
    private const val MIN_NOTE_FRAMES = 3
    private const val MAX_GAP_FRAMES = 1
    private const val MAX_PITCH_DEVIATION = 0.7

    /**
     * @param pcm mono 16 bit samples
     * @param sampleRate sample rate of [pcm]. Must be a multiple of 4
     */
    fun analyze(pcm: ShortArray, sampleRate: Int = 48000): List<DetectedNote> {
        val rate = sampleRate / DECIMATION
        val samples = decimate(pcm)
        val frameCount = (samples.size - WINDOW - MAX_TAU - 1) / HOP + 1
        if (frameCount <= 0) return emptyList()

        // Loudness of every frame. Frames that are much quieter than the loud parts are treated as silence
        val rms = FloatArray(frameCount) { frameRms(samples, it * HOP) }
        val sortedRms = rms.sortedArray()
        val reference = sortedRms[(frameCount * 0.9f).toInt().coerceAtMost(frameCount - 1)]
        val gate = maxOf(reference * 0.15f, 30f)

        val pitches = DoubleArray(frameCount) { Double.NaN }
        val diff = DoubleArray(MAX_TAU + 2)
        val cmnd = DoubleArray(MAX_TAU + 2)
        for (frame in 0 until frameCount) {
            if (rms[frame] < gate) continue
            val frequency = estimateFrequency(samples, frame * HOP, rate, diff, cmnd)
            if (frequency > 0.0) {
                pitches[frame] = 12.0 * ln(frequency / 440.0) / ln(2.0)
            }
        }

        return segment(pitches, rate)
    }

    private fun decimate(pcm: ShortArray): FloatArray {
        val out = FloatArray(pcm.size / DECIMATION)
        for (i in out.indices) {
            var sum = 0f
            for (k in 0 until DECIMATION) sum += pcm[i * DECIMATION + k]
            out[i] = sum / DECIMATION
        }
        return out
    }

    private fun frameRms(samples: FloatArray, start: Int): Float {
        var sum = 0.0
        for (i in start until start + WINDOW) sum += samples[i] * samples[i]
        return Math.sqrt(sum / WINDOW).toFloat()
    }

    /**
     * YIN pitch estimate for the frame starting at [start]. Returns 0 if the frame is not clearly pitched.
     */
    private fun estimateFrequency(samples: FloatArray, start: Int, rate: Int, diff: DoubleArray, cmnd: DoubleArray): Double {
        for (tau in 1..MAX_TAU + 1) {
            var sum = 0.0
            for (j in 0 until WINDOW) {
                val delta = samples[start + j] - samples[start + j + tau]
                sum += delta * delta
            }
            diff[tau] = sum
        }

        cmnd[0] = 1.0
        var running = 0.0
        for (tau in 1..MAX_TAU + 1) {
            running += diff[tau]
            cmnd[tau] = if (running > 0.0) diff[tau] * tau / running else 1.0
        }

        var tau = MIN_TAU
        while (tau <= MAX_TAU) {
            if (cmnd[tau] < YIN_THRESHOLD) {
                // Follow the dip down to its bottom
                while (tau + 1 <= MAX_TAU && cmnd[tau + 1] < cmnd[tau]) tau++
                val refined = parabolicInterpolation(cmnd, tau)
                return rate / refined
            }
            tau++
        }
        return 0.0
    }

    private fun parabolicInterpolation(values: DoubleArray, index: Int): Double {
        val a = values[index - 1]
        val b = values[index]
        val c = values[index + 1]
        val denominator = a - 2 * b + c
        if (denominator == 0.0) return index.toDouble()
        return index + 0.5 * (a - c) / denominator
    }

    private fun segment(pitches: DoubleArray, rate: Int): List<DetectedNote> {
        val notes = mutableListOf<DetectedNote>()
        var firstFrame = -1
        var lastFrame = -1
        val current = mutableListOf<Double>()

        fun frameTimeMs(frame: Int): Long = ((frame * HOP + WINDOW / 2) * 1000L) / rate

        fun closeNote() {
            if (firstFrame >= 0 && current.size >= MIN_NOTE_FRAMES) {
                val sorted = current.sorted()
                val median = sorted[sorted.size / 2]
                val halfHop = HOP * 500L / rate
                notes.add(DetectedNote(frameTimeMs(firstFrame) - halfHop, frameTimeMs(lastFrame) + halfHop, median))
            }
            firstFrame = -1
            lastFrame = -1
            current.clear()
        }

        for (frame in pitches.indices) {
            val pitch = pitches[frame]
            if (pitch.isNaN()) continue

            if (firstFrame >= 0) {
                val median = current.sorted()[current.size / 2]
                val gap = frame - lastFrame - 1
                if (gap > MAX_GAP_FRAMES || abs(pitch - median) > MAX_PITCH_DEVIATION) {
                    closeNote()
                }
            }

            if (firstFrame < 0) firstFrame = frame
            lastFrame = frame
            current.add(pitch)
        }
        closeNote()

        return notes
    }
}
