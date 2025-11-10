package me.magnum.melonds.ui.common

import android.graphics.Rect
import android.os.Build
import android.view.View
import androidx.core.view.ViewCompat

/**
 * Utility to manage system gesture exclusion rectangles for views that need to
 * consume edge gestures (e.g., to avoid triggering the back gesture).
 */
class SystemGestureExclusionHelper {

    private val listeners = mutableMapOf<View, View.OnLayoutChangeListener>()

    fun setGestureExclusion(view: View?, enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }

        val targetView = view ?: return
        if (!enabled) {
            listeners.remove(targetView)?.let { targetView.removeOnLayoutChangeListener(it) }
            ViewCompat.setSystemGestureExclusionRects(targetView, emptyList())
            return
        }

        val listener = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            updateGestureRect(v)
        }

        listeners.remove(targetView)?.let { targetView.removeOnLayoutChangeListener(it) }
        targetView.addOnLayoutChangeListener(listener)
        listeners[targetView] = listener
        updateGestureRect(targetView)
    }

    fun clearAll() {
        val iterator = listeners.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val view = entry.key
            view.removeOnLayoutChangeListener(entry.value)
            ViewCompat.setSystemGestureExclusionRects(view, emptyList())
            iterator.remove()
        }
    }

    private fun updateGestureRect(view: View) {
        if (view.width <= 0 || view.height <= 0) {
            ViewCompat.setSystemGestureExclusionRects(view, emptyList())
            return
        }
        val rect = Rect(0, 0, view.width, view.height)
        ViewCompat.setSystemGestureExclusionRects(view, listOf(rect))
    }
}
