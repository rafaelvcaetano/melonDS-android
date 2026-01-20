package me.magnum.melonds.ui.emulator.rewind.model

import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class RewindWindow(
    val currentEmulationFrame: Int,
    val rewindStates: ArrayList<RewindSaveState>
) {

    companion object {
        private const val FRAMES_PER_SECOND = 60
    }

    fun getDeltaFromEmulationTimeToRewindState(state: RewindSaveState): Duration {
        val elapsedFrames = currentEmulationFrame - state.frame
        val elapsedMillis = elapsedFrames.toFloat() / FRAMES_PER_SECOND * 1000
        return elapsedMillis.toLong().milliseconds
    }
}