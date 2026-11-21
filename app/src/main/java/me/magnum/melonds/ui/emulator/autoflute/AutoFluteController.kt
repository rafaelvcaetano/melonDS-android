package me.magnum.melonds.ui.emulator.autoflute

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.domain.model.Input

/**
 * Records and replays flute performances (e.g. the Spirit Flute in Zelda: Spirit Tracks).
 *
 * A performance is the path of the stylus over the touch screen. While replaying, the stylus path is injected as
 * touch input and the emulated microphone is switched to "blowing" for as long as the stylus is down, which is what
 * the flute needs to produce sound.
 *
 * Time is measured on a clock that stops while the emulator is paused (pause menu, dialogs, app in background), so
 * that recordings don't contain pauses and playback doesn't skip ahead while the emulator is not running.
 *
 * Must be used from the main thread.
 */
class AutoFluteController(private val scope: CoroutineScope) {
    enum class State {
        IDLE,
        /** The player is playing a song by hand to save it */
        RECORDING,
        /** The player is touching every pipe once so that the app learns which position makes which pitch */
        CALIBRATING,
        /** The game's audio is being recorded while a character plays a song */
        CAPTURING,
        PLAYING,
    }

    /**
     * Result of a calibration run. [firstEventOffsetMs] is when the first stylus event happened, measured from
     * the start of the audio capture.
     */
    class CalibrationCapture(val events: List<FluteEvent>, val firstEventOffsetMs: Long, val pcm: ShortArray)

    companion object {
        // Gives the player time to get back to the game before the first note
        private const val PLAYBACK_LEAD_IN_MS = 600L

        // Upper bound for audio captures. Memory use is about 5.8 MB per minute
        private const val MAX_CAPTURE_SECONDS = 120

        private const val MIC_MODE_DISABLED = 0
        private const val MIC_MODE_SILENCE = 1
        private const val MIC_MODE_BLOW = 2
    }

    var state = State.IDLE
        private set

    val isPlaying: Boolean get() = state == State.PLAYING
    val isRecording: Boolean get() = state == State.RECORDING

    private val isRecordingStylus: Boolean get() = state == State.RECORDING || state == State.CALIBRATING

    /** Invoked on the main thread whenever playback finishes by itself or is stopped. */
    var onPlaybackFinished: (() -> Unit)? = null

    // Pause-aware clock
    private var overlaysPresent = false
    private var activityPaused = false
    private var pausedSince = -1L
    private var totalPausedMs = 0L

    // Recording
    private val recordedEvents = mutableListOf<FluteEvent>()
    private var recordingStartTime = -1L
    private var recordingBeginTime = 0L
    private var stylusDown = false
    private var lastX = -1
    private var lastY = -1

    // Playback
    private var playbackJob: Job? = null
    private var playbackStylusDown = false

    fun setOverlaysPresent(present: Boolean) {
        overlaysPresent = present
        updateClockPaused()
    }

    fun setActivityPaused(paused: Boolean) {
        activityPaused = paused
        updateClockPaused()
    }

    private fun updateClockPaused() {
        val shouldBePaused = overlaysPresent || activityPaused
        val real = SystemClock.elapsedRealtime()
        if (shouldBePaused && pausedSince < 0) {
            pausedSince = real
        } else if (!shouldBePaused && pausedSince >= 0) {
            totalPausedMs += real - pausedSince
            pausedSince = -1
        }
    }

    private fun now(): Long {
        val real = SystemClock.elapsedRealtime()
        val currentPause = if (pausedSince >= 0) real - pausedSince else 0L
        return real - totalPausedMs - currentPause
    }

    // ---------------------------------------------------------------- Recording

    fun startRecording() {
        beginStylusRecording(State.RECORDING)
    }

    private fun beginStylusRecording(newState: State) {
        stop()
        recordedEvents.clear()
        recordingStartTime = -1L
        recordingBeginTime = now()
        stylusDown = false
        lastX = -1
        lastY = -1
        state = newState
    }

    /**
     * Stops recording and returns the recorded events, or null if nothing was played.
     */
    fun stopRecording(): List<FluteEvent>? {
        if (state != State.RECORDING) return null

        if (stylusDown) {
            addRecordedEvent(FluteEvent.Type.UP, lastX, lastY)
            stylusDown = false
        }
        state = State.IDLE
        return recordedEvents.toList().takeIf { it.isNotEmpty() }
    }

    fun discardRecording() {
        if (state == State.RECORDING) {
            state = State.IDLE
            recordedEvents.clear()
        }
    }

    // ---------------------------------------------------------------- Calibration and audio capture

    fun startCalibration() {
        beginStylusRecording(State.CALIBRATING)
        MelonEmulator.startAudioCapture(MAX_CAPTURE_SECONDS)
        // While calibrating, the game only hears blowing when the stylus is down so that the device's microphone
        // (and any background noise) can't influence the recorded pitches
        MelonEmulator.setAutoMicMode(MIC_MODE_SILENCE)
    }

    fun finishCalibration(): CalibrationCapture? {
        if (state != State.CALIBRATING) return null

        if (stylusDown) {
            addRecordedEvent(FluteEvent.Type.UP, lastX, lastY)
            stylusDown = false
        }
        state = State.IDLE
        MelonEmulator.setAutoMicMode(MIC_MODE_DISABLED)
        val pcm = MelonEmulator.stopAudioCapture()

        val events = recordedEvents.toList()
        if (events.isEmpty()) return null
        return CalibrationCapture(events, recordingStartTime - recordingBeginTime, pcm)
    }

    fun startSongCapture() {
        stop()
        state = State.CAPTURING
        MelonEmulator.startAudioCapture(MAX_CAPTURE_SECONDS)
    }

    /**
     * Stops capturing the game's audio and returns the captured samples (mono, 48 kHz).
     */
    fun finishSongCapture(): ShortArray? {
        if (state != State.CAPTURING) return null
        state = State.IDLE
        return MelonEmulator.stopAudioCapture()
    }

    // Called by the touch input handler. These are real user inputs

    fun onStylusDown(x: Int, y: Int) {
        if (!isRecordingStylus) return
        if (state == State.CALIBRATING) MelonEmulator.setAutoMicMode(MIC_MODE_BLOW)
        stylusDown = true
        lastX = x
        lastY = y
        addRecordedEvent(FluteEvent.Type.DOWN, x, y)
    }

    fun onStylusMove(x: Int, y: Int) {
        if (!isRecordingStylus || !stylusDown) return
        if (x == lastX && y == lastY) return
        lastX = x
        lastY = y
        addRecordedEvent(FluteEvent.Type.MOVE, x, y)
    }

    fun onStylusUp() {
        if (!isRecordingStylus || !stylusDown) return
        if (state == State.CALIBRATING) MelonEmulator.setAutoMicMode(MIC_MODE_SILENCE)
        stylusDown = false
        addRecordedEvent(FluteEvent.Type.UP, lastX, lastY)
    }

    private fun addRecordedEvent(type: FluteEvent.Type, x: Int, y: Int) {
        val currentTime = now()
        if (recordingStartTime < 0) {
            // The song starts at the first stylus contact so that any idle time before it isn't recorded
            recordingStartTime = currentTime
        }
        recordedEvents.add(FluteEvent(currentTime - recordingStartTime, type, x, y))
    }

    // ---------------------------------------------------------------- Playback

    fun play(song: FluteSong, speed: Float = 1f) {
        stop()
        if (song.events.isEmpty()) return

        state = State.PLAYING
        playbackJob = scope.launch {
            val self = coroutineContext[Job]
            try {
                // From now on the game only hears what the auto flute decides to play
                MelonEmulator.setAutoMicMode(MIC_MODE_SILENCE)

                val startTime = now() + PLAYBACK_LEAD_IN_MS
                for (event in song.events) {
                    val dueTime = startTime + (event.timeMs / speed).toLong()
                    while (true) {
                        val remaining = dueTime - now()
                        if (remaining <= 0) break
                        delay(remaining.coerceIn(1L, 10L))
                    }
                    applyPlaybackEvent(event)
                }

                // Hold the final state for a moment so that the last note isn't cut short
                delay(100)
            } finally {
                // If stop() or a new play() replaced this job, they have already cleaned up. Don't clobber their state
                if (playbackJob === self) {
                    finishPlayback()
                }
            }
        }
    }

    private fun finishPlayback() {
        playbackJob = null
        releasePlaybackInput()
        state = State.IDLE
        onPlaybackFinished?.invoke()
    }

    private fun applyPlaybackEvent(event: FluteEvent) {
        when (event.type) {
            FluteEvent.Type.DOWN -> {
                playbackStylusDown = true
                MelonEmulator.onInputDown(Input.TOUCHSCREEN)
                MelonEmulator.onScreenTouch(event.x, event.y)
                MelonEmulator.setAutoMicMode(MIC_MODE_BLOW)
            }
            FluteEvent.Type.MOVE -> {
                if (playbackStylusDown) {
                    MelonEmulator.onScreenTouch(event.x, event.y)
                }
            }
            FluteEvent.Type.UP -> {
                releasePlaybackStylus()
                MelonEmulator.setAutoMicMode(MIC_MODE_SILENCE)
            }
        }
    }

    private fun releasePlaybackStylus() {
        if (playbackStylusDown) {
            playbackStylusDown = false
            MelonEmulator.onScreenRelease()
            MelonEmulator.onInputUp(Input.TOUCHSCREEN)
        }
    }

    private fun releasePlaybackInput() {
        releasePlaybackStylus()
        MelonEmulator.setAutoMicMode(MIC_MODE_DISABLED)
    }

    /**
     * Stops playback or discards whatever is being recorded or captured.
     */
    fun stop() {
        when (state) {
            State.PLAYING -> {
                playbackJob?.cancel()
                finishPlayback()
            }
            State.RECORDING -> discardRecording()
            State.CALIBRATING -> {
                state = State.IDLE
                MelonEmulator.setAutoMicMode(MIC_MODE_DISABLED)
                MelonEmulator.stopAudioCapture()
            }
            State.CAPTURING -> {
                state = State.IDLE
                MelonEmulator.stopAudioCapture()
            }
            State.IDLE -> { /* Nothing to do */ }
        }
    }
}
