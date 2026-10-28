package me.magnum.melonds.ui.cheats

import android.os.Bundle
import android.os.SystemClock
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inspector.WindowInspector
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Opt-in visual diagnostic against an installed APK, with an imported synthetic database.
 * Run with -e captureUi true -e captureLabel baseline; this is not a gameplay test. */
@RunWith(AndroidJUnit4::class)
class CheatUiCaptureTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation

    private fun find(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        fun visit(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (predicate(node)) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { visit(it)?.let { found -> return found } }
            return null
        }
        return automation.rootInActiveWindow?.let(::visit)
    }

    private fun awaitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < deadline) {
            find(predicate)?.let { return it }
            SystemClock.sleep(50)
        }
        error("Expected native UI node did not appear")
    }

    private fun clickText(text: String) {
        var node = awaitNode { it.text?.toString() == text && !it.isEditable }
        while (!node.isClickable) node = node.parent ?: error("No clickable parent for $text")
        check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    private fun query(text: String) {
        val input = awaitNode { it.isEditable }
        check(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }))
    }

    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        val label = InstrumentationRegistry.getArguments().getString("captureLabel", "capture")
        require(label.matches(Regex("[a-zA-Z0-9_-]+")))
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-captures").apply { mkdirs() }
        val bitmap = checkNotNull(automation.takeScreenshot())
        File(folder, "$label-$name.png").outputStream().use {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
        instrumentation.runOnMainSync {
            val roots = JSONArray()
            for (view in WindowInspector.getGlobalWindowViews()) {
                val frame = Rect().also { view.getWindowVisibleDisplayFrame(it) }
                val location = IntArray(2).also { view.getLocationOnScreen(it) }
                val insets = view.rootWindowInsets
                roots.put(JSONObject().apply {
                    put("class", view.javaClass.name)
                    put("size", "${view.width}x${view.height}")
                    put("measured", "${view.measuredWidth}x${view.measuredHeight}")
                    put("location", location.joinToString(","))
                    put("visibleFrame", frame.toShortString())
                    put("softInputMode", (view.layoutParams as? WindowManager.LayoutParams)?.softInputMode)
                    put("ime", insets?.getInsets(WindowInsets.Type.ime()).toString())
                    put("systemBars", insets?.getInsets(WindowInsets.Type.systemBars()).toString())
                })
            }
            File(folder, "$label-$name-windows.json").writeText(roots.toString(2))
        }
    }

    @Test fun captureSyntheticSearchAndModifier() {
        assumeTrue("Opt-in visual capture; excluded from correctness pass counts",
            InstrumentationRegistry.getArguments().getString("captureUi") == "true")
        ActivityScenario.launch(CheatsActivity::class.java).use {
            try {
                clickText("Synthetic UI fixture")
                // Wait for the destination, rather than editing the departing game search.
                awaitNode { it.text?.toString() == "Other synthetic cheats" }
                automation.waitForIdle(200, 5000)
                query("Short")
                awaitNode { it.text?.toString() == "Short result" }
                capture("search")
                query("Charmander")
                clickText("Charmander")
                awaitNode { it.text?.toString() in setOf("Enable", "Attiva") }
                capture("modifier")
                if (InstrumentationRegistry.getArguments().getString("captureIme") == "true") {
                    automation.serviceInfo = automation.serviceInfo.apply {
                        flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    }
                    val bounds = Rect().also { awaitNode { it.isEditable }.getBoundsInScreen(it) }
                    val time = SystemClock.uptimeMillis()
                    for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                        MotionEvent.obtain(time, SystemClock.uptimeMillis(), action,
                            bounds.exactCenterX(), bounds.exactCenterY(), 0).let { event ->
                            try { check(automation.injectInputEvent(event, true)) } finally { event.recycle() }
                        }
                    }
                    val deadline = SystemClock.uptimeMillis() + 5000
                    while (automation.windows.none { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } &&
                        SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
                    check(automation.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }) {
                        "IME capture requires an actual input-method window"
                    }
                    automation.waitForIdle(200, 5000)
                    // Diagnostic settling interval; window presence above is the IME oracle.
                    SystemClock.sleep(1000)
                    capture("modifier-ime")
                    if (InstrumentationRegistry.getArguments().getString("captureImeDone") == "true") {
                        check(awaitNode { it.isEditable }.performAction(
                            AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)) {
                            "The query must expose the keyboard action"
                        }
                        val hiddenDeadline = SystemClock.uptimeMillis() + 5000
                        while (automation.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } &&
                            SystemClock.uptimeMillis() < hiddenDeadline) SystemClock.sleep(50)
                        check(automation.windows.none { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }) {
                            "Done must dismiss the actual keyboard"
                        }
                        awaitNode { it.text?.toString() in setOf("Enable", "Attiva") && it.isVisibleToUser }
                        automation.waitForIdle(200, 5000)
                        capture("modifier-ime-done")
                    }
                }
            } catch (error: Exception) {
                capture("failure")
                throw error
            }
        }
    }
}
