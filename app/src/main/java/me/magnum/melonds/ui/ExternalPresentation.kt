package me.magnum.melonds.ui

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.opengl.GLSurfaceView
import android.view.Display
import android.view.View
import android.widget.FrameLayout
import me.magnum.melonds.common.runtime.ScreenshotFrameBufferProvider
import me.magnum.melonds.domain.model.DsScreen
import me.magnum.melonds.ui.DSScreenRenderer
import me.magnum.melonds.ui.DSLayoutRenderer
import me.magnum.melonds.domain.model.Rect

/**
 * Presentation shown on an external display. For now it only shows
 * a solid background color. In the future it will render the emulator's
 * top screen here.
 */
class ExternalPresentation(context: Context, display: Display) : Presentation(context, display) {
    private val container = FrameLayout(context)

    // Surface currently displayed. It initially shows a simple color renderer
    // but can be replaced with a renderer displaying the emulator's top screen.
    private var surfaceView: GLSurfaceView
    private val placeholderRenderer = ColorRenderer(Color.parseColor("#8B0000"))

    init {
        surfaceView = GLSurfaceView(context).apply {
            setEGLContextClientVersion(3)
            setRenderer(placeholderRenderer)
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }
        container.addView(surfaceView)
        setContentView(container)
    }

    fun setBackground(color: Int) {
        placeholderRenderer.color = color
        surfaceView.requestRender()
    }

    /**
     * Attach a view to be rendered on the external display instead of the
     * placeholder. This will typically be a MelonView showing the top screen.
     * TODO: redirect the emulator's top screen output here.
     */
    fun attachView(view: View) {
        container.removeAllViews()
        container.addView(view)
        if (view is GLSurfaceView) {
            surfaceView = view
        }
    }

    /**
     * Replace the placeholder surface with a renderer displaying the emulator's
     * top screen. Returns the created renderer so callers can request renders
     * when new frames are available.
     */
    fun showDsScreen(provider: ScreenshotFrameBufferProvider, screen: DsScreen): DSScreenRenderer {
        val renderer = DSScreenRenderer(provider, screen)
        val view = GLSurfaceView(context).apply {
            setEGLContextClientVersion(3)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }
        attachView(view)
        return renderer
    }

    /**
     * Display both DS screens using the provided layout rectangles.
     */
    fun showCustomLayout(
        provider: ScreenshotFrameBufferProvider,
        topRect: Rect?,
        bottomRect: Rect?
    ): DSLayoutRenderer {
        val renderer = DSLayoutRenderer(provider, topRect, bottomRect)
        val view = GLSurfaceView(context).apply {
            setEGLContextClientVersion(3)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }
        attachView(view)
        return renderer
    }

    fun showTopScreen(provider: ScreenshotFrameBufferProvider) = showDsScreen(provider, DsScreen.TOP)
    fun showBottomScreen(provider: ScreenshotFrameBufferProvider) = showDsScreen(provider, DsScreen.BOTTOM)

    fun requestRender() {
        surfaceView.requestRender()
    }
}
