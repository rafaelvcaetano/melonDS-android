// SPDX-License-Identifier: GPL-3.0-or-later
package me.magnum.melonds.ui.emulator.input

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.InputDevice
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.magnum.melonds.common.vibration.TouchVibrator
import me.magnum.melonds.common.vibration.VibratorDelegate
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.Point
import me.magnum.melonds.domain.repositories.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy

/** Real Android events, views and production handlers; no ROM or emulator session. */
@RunWith(AndroidJUnit4::class)
class TouchCancellationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private var gestureDownTime = 0L

    @Test
    fun singleButtonReleasesOnCancel() = onMain {
        val listener = RecordingListener()
        val view = feedbackView(SingleButtonInputHandler(listener, Input.L, true, unusedVibrator()))
        dispatch(view, MotionEvent.ACTION_DOWN)
        dispatch(view, MotionEvent.ACTION_CANCEL)
        assertEquals(listOf("down:L", "up:L"), listener.events)
        assertEquals(listOf(HapticFeedbackConstants.LONG_PRESS), view.feedback)
    }

    @Test
    fun touchscreenReleasesOnCancel() = onMain {
        val listener = RecordingListener()
        val view = inputView(TouchscreenInputHandler(listener))
        dispatch(view, MotionEvent.ACTION_DOWN)
        dispatch(view, MotionEvent.ACTION_CANCEL)
        assertEquals(listOf("down:TOUCHSCREEN", "touch:128,96", "up:TOUCHSCREEN"), listener.events)
    }

    @Test
    fun singleButtonStillReleasesOnUp() = onMain {
        val listener = RecordingListener()
        val view = feedbackView(SingleButtonInputHandler(listener, Input.L, true, unusedVibrator()))
        dispatch(view, MotionEvent.ACTION_DOWN)
        dispatch(view, MotionEvent.ACTION_MOVE)
        dispatch(view, MotionEvent.ACTION_UP)
        assertEquals(listOf("down:L", "up:L"), listener.events)
        assertEquals(listOf(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.CLOCK_TICK), view.feedback)
    }

    @Test
    fun touchscreenStillMovesAndReleasesOnUp() = onMain {
        val listener = RecordingListener()
        val view = inputView(TouchscreenInputHandler(listener))
        dispatch(view, MotionEvent.ACTION_DOWN)
        dispatch(view, MotionEvent.ACTION_MOVE, 64f, 48f)
        dispatch(view, MotionEvent.ACTION_UP, 64f, 48f)
        assertEquals(listOf("down:TOUCHSCREEN", "touch:128,96", "touch:64,48", "up:TOUCHSCREEN"), listener.events)
    }

    @Test
    fun dpadStillReleasesOnCancel() = onMain {
        val listener = RecordingListener()
        val view = inputView(DpadInputHandler(listener, false, unusedVibrator()), 512, 512)
        dispatch(view, MotionEvent.ACTION_DOWN, 480f, 256f)
        dispatch(view, MotionEvent.ACTION_CANCEL, 480f, 256f)
        assertEquals(listOf("down:RIGHT", "up:RIGHT"), listener.events)
    }

    @Test
    fun touchscreenAcceptsFreshGestureAfterCancel() = onMain {
        val listener = RecordingListener()
        val view = inputView(TouchscreenInputHandler(listener))
        dispatch(view, MotionEvent.ACTION_DOWN)
        dispatch(view, MotionEvent.ACTION_CANCEL)
        dispatch(view, MotionEvent.ACTION_DOWN, 64f, 48f)
        dispatch(view, MotionEvent.ACTION_UP, 64f, 48f)
        assertEquals(listOf("down:TOUCHSCREEN", "touch:128,96", "up:TOUCHSCREEN", "down:TOUCHSCREEN", "touch:64,48", "up:TOUCHSCREEN"), listener.events)
    }

    @Test
    fun interceptedButtonGestureReleasesWithoutAnUpEvent() = onMain {
        val listener = RecordingListener()
        val child = inputView(SingleButtonInputHandler(listener, Input.L, false, unusedVibrator()))
        interceptGesture(child)
        assertEquals(listOf("down:L", "up:L"), listener.events)
    }

    @Test
    fun interceptedTouchscreenGestureReleasesWithoutAnUpEvent() = onMain {
        val listener = RecordingListener()
        val child = inputView(TouchscreenInputHandler(listener))
        interceptGesture(child)
        assertEquals(listOf("down:TOUCHSCREEN", "touch:128,96", "up:TOUCHSCREEN"), listener.events)
    }

    @Test
    fun touchscreenKeepsAveragingMultiplePointersUntilFinalUp() = onMain {
        val listener = RecordingListener()
        val view = inputView(TouchscreenInputHandler(listener), 512, 384)
        dispatchPointers(view, MotionEvent.ACTION_DOWN, 64f to 48f)
        dispatchPointers(view, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 64f to 48f, 448f to 336f)
        dispatchPointers(view, MotionEvent.ACTION_MOVE, 64f to 48f, 448f to 336f)
        dispatchPointers(view, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 64f to 48f, 448f to 336f)
        dispatchPointers(view, MotionEvent.ACTION_MOVE, 64f to 48f)
        dispatchPointers(view, MotionEvent.ACTION_UP, 64f to 48f)
        assertEquals(listOf("down:TOUCHSCREEN", "touch:32,24", "touch:128,96", "touch:32,24", "up:TOUCHSCREEN"), listener.events)
    }

    @Test
    fun touchscreenReleasesAllPointersOnCancel() = onMain {
        val listener = RecordingListener()
        val view = inputView(TouchscreenInputHandler(listener))
        dispatchPointers(view, MotionEvent.ACTION_DOWN, 32f to 24f)
        dispatchPointers(view, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 32f to 24f, 224f to 168f)
        dispatchPointers(view, MotionEvent.ACTION_MOVE, 32f to 24f, 224f to 168f)
        dispatchPointers(view, MotionEvent.ACTION_CANCEL, 32f to 24f, 224f to 168f)
        assertEquals(listOf("down:TOUCHSCREEN", "touch:32,24", "touch:128,96", "up:TOUCHSCREEN"), listener.events)
    }

    @Test
    fun touchscreenStillClampsCoordinatesToDsBounds() = onMain {
        val listener = RecordingListener()
        val view = inputView(TouchscreenInputHandler(listener))
        dispatch(view, MotionEvent.ACTION_DOWN, -10f, -10f)
        dispatch(view, MotionEvent.ACTION_MOVE, 300f, 200f)
        dispatch(view, MotionEvent.ACTION_UP, 300f, 200f)
        assertEquals(listOf("down:TOUCHSCREEN", "touch:0,0", "touch:255,191", "up:TOUCHSCREEN"), listener.events)
    }

    @Test
    fun singleButtonIgnoresSecondaryPointerTransitionsUntilFinalUp() = onMain {
        val listener = RecordingListener()
        val view = inputView(SingleButtonInputHandler(listener, Input.L, false, unusedVibrator()))
        dispatchPointers(view, MotionEvent.ACTION_DOWN, 32f to 24f)
        dispatchPointers(view, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 32f to 24f, 224f to 168f)
        dispatchPointers(view, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 32f to 24f, 224f to 168f)
        dispatchPointers(view, MotionEvent.ACTION_UP, 32f to 24f)
        assertEquals(listOf("down:L", "up:L"), listener.events)
    }

    private fun dispatchPointers(view: View, action: Int, vararg coordinates: Pair<Float, Float>) {
        val properties = Array(coordinates.size) { index -> MotionEvent.PointerProperties().apply { id = index; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val positions = Array(coordinates.size) { index -> MotionEvent.PointerCoords().apply { x = coordinates[index].first; y = coordinates[index].second; pressure = 1f; size = 1f } }
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) gestureDownTime = now
        val event = MotionEvent.obtain(gestureDownTime, now, action, coordinates.size, properties, positions, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try {
            assertTrue("Event $action was not consumed", view.dispatchTouchEvent(event))
        } finally {
            event.recycle()
        }
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private class FeedbackView(context: Context) : View(context) {
        val feedback = mutableListOf<Int>()
        override fun performHapticFeedback(feedbackConstant: Int): Boolean {
            feedback += feedbackConstant
            return true
        }
    }

    private fun feedbackView(handler: View.OnTouchListener) = FeedbackView(context).apply {
        layout(0, 0, 256, 192)
        setOnTouchListener(handler)
    }

    private fun inputView(handler: View.OnTouchListener, width: Int = 256, height: Int = 192): View {
        return View(context).apply {
            layout(0, 0, width, height)
            setOnTouchListener(handler)
        }
    }

    private fun interceptGesture(child: View) {
        val parent = object : FrameLayout(context) {
            override fun onInterceptTouchEvent(event: MotionEvent): Boolean = event.actionMasked == MotionEvent.ACTION_MOVE
            override fun onTouchEvent(event: MotionEvent): Boolean = true
        }
        parent.addView(child, FrameLayout.LayoutParams(256, 192))
        parent.layout(0, 0, 256, 192)
        child.layout(0, 0, 256, 192)
        dispatch(parent, MotionEvent.ACTION_DOWN)
        // ViewGroup generates ACTION_CANCEL for the child when it intercepts this move.
        // No ACTION_UP is sent by the test.
        dispatch(parent, MotionEvent.ACTION_MOVE)
    }

    private fun dispatch(view: View, action: Int, x: Float = 128f, y: Float = 96f) {
        val eventTime = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) gestureDownTime = eventTime
        val event = MotionEvent.obtain(gestureDownTime, eventTime, action, x, y, 0)
        try {
            assertTrue("Event $action was not consumed", view.dispatchTouchEvent(event))
        } finally {
            event.recycle()
        }
    }

    private class RecordingListener : IInputListener {
        val events = mutableListOf<String>()
        override fun onKeyPress(key: Input) { events += "down:$key" }
        override fun onKeyReleased(key: Input) { events += "up:$key" }
        override fun onTouch(point: Point) { events += "touch:${point.x},${point.y}" }
    }

    private fun unusedVibrator(): TouchVibrator {
        // The constructor requires these dependencies, but handlers use View haptics.
        // Fail if a future change unexpectedly accesses settings or vibration.
        val settings = Proxy.newProxyInstance(SettingsRepository::class.java.classLoader, arrayOf(SettingsRepository::class.java)) { _, method, _ ->
            error("Unexpected settings access: ${method.name}")
        } as SettingsRepository
        val delegate = object : VibratorDelegate {
            override fun supportsVibration(): Boolean = error("Unexpected vibration query")
            override fun supportsVibrationAmplitude(): Boolean = error("Unexpected amplitude query")
            override fun vibrate(duration: Int, amplitude: Int) = error("Unexpected vibration")
            override fun startVibrating() = error("Unexpected vibration")
            override fun stopVibrating() = error("Unexpected vibration")
        }
        return TouchVibrator(delegate, settings)
    }
}
