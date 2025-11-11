package me.magnum.melonds.ui.layouteditor

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import androidx.core.content.ContextCompat
import com.squareup.picasso.Picasso
import me.magnum.melonds.domain.model.Point
import me.magnum.melonds.domain.model.RuntimeBackground
import me.magnum.melonds.domain.model.layout.UILayout

class LayoutEditorPreviewController(
    private val context: Context,
    private val picasso: Picasso,
) {

    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private val displayHandler = Handler(Looper.getMainLooper())
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            showPresentationIfPossible()
        }

        override fun onDisplayRemoved(displayId: Int) {
            if (presentation?.display?.displayId == displayId) {
                hidePresentation()
            }
        }

        override fun onDisplayChanged(displayId: Int) {
            if (presentation?.display?.displayId == displayId) {
                presentation?.updateLayout(latestLayout, latestSourceSize)
                presentation?.updateBackground(latestBackground)
            }
        }
    }

    private var presentation: LayoutEditorPreviewPresentation? = null
    private var latestLayout: UILayout? = null
    private var latestSourceSize: Point? = null
    private var latestBackground: RuntimeBackground? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        displayManager?.registerDisplayListener(displayListener, displayHandler)
        showPresentationIfPossible()
    }

    fun stop() {
        if (!started) return
        started = false
        hidePresentation()
        displayManager?.unregisterDisplayListener(displayListener)
    }

    fun updateLayout(layout: UILayout?, sourceSize: Point?) {
        latestLayout = layout
        latestSourceSize = sourceSize
        presentation?.updateLayout(layout, sourceSize)
    }

    fun updateBackground(background: RuntimeBackground?) {
        latestBackground = background
        presentation?.updateBackground(background)
    }

    private fun showPresentationIfPossible() {
        if (!started || presentation != null) return
        val display = chooseTargetDisplay() ?: return
        val newPresentation = LayoutEditorPreviewPresentation(context, display, picasso).apply {
            setOnDismissListener {
                if (presentation == this) {
                    presentation = null
                }
            }
            show()
        }
        presentation = newPresentation
        latestBackground?.let { newPresentation.updateBackground(it) }
        newPresentation.updateLayout(latestLayout, latestSourceSize)
    }

    private fun hidePresentation() {
        presentation?.dismiss()
        presentation = null
    }

    private fun chooseTargetDisplay(): Display? {
        val dm = displayManager ?: return null
        val currentDisplay = ContextCompat.getDisplayOrDefault(context)
        return if (currentDisplay.displayId != Display.DEFAULT_DISPLAY) {
            dm.getDisplay(Display.DEFAULT_DISPLAY)
        } else {
            dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
                .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && it.name != "HiddenDisplay" }
        }
    }
}
