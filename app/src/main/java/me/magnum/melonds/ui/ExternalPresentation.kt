package me.magnum.melonds.ui

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.opengl.GLSurfaceView
import me.magnum.melonds.common.opengl.SharedEglContextFactory
import javax.microedition.khronos.egl.EGLContext as EGL10Context
import android.view.Display
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import me.magnum.melonds.ui.emulator.input.IInputListener
import me.magnum.melonds.ui.emulator.input.TouchscreenInputHandler
import me.magnum.melonds.domain.model.DsScreen
import me.magnum.melonds.ui.DSScreenRenderer
import me.magnum.melonds.ui.DSLayoutRenderer
import me.magnum.melonds.domain.model.Rect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import me.magnum.melonds.ui.emulator.ui.AchievementList
import me.magnum.melonds.ui.romlist.RomListRetroAchievementsViewModel
import me.magnum.melonds.ui.theme.MelonTheme

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
    private var touchOverlay: View? = null
    private var sharedContext: EGL10Context? = null

    init {
        surfaceView = GLSurfaceView(context).apply {
            setEGLContextClientVersion(3)
            setRenderer(placeholderRenderer)
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }
        container.addView(surfaceView)

        // Propagate lifecycle and state owners from the host Activity so
        // Compose content inside the presentation can access them.
        (context as? LifecycleOwner)?.let { owner ->
            container.setViewTreeLifecycleOwner(owner)
        }
        (context as? ViewModelStoreOwner)?.let { owner ->
            container.setViewTreeViewModelStoreOwner(owner)
        }
        (context as? SavedStateRegistryOwner)?.let { owner ->
            container.setViewTreeSavedStateRegistryOwner(owner)
        }

        setContentView(container)
    }

    fun setSharedContext(context: EGL10Context?) {
        sharedContext = context
    }

    fun setOrientation(orientation: me.magnum.melonds.domain.model.layout.LayoutConfiguration.LayoutOrientation) {
        container.rotation = when (orientation) {
            me.magnum.melonds.domain.model.layout.LayoutConfiguration.LayoutOrientation.PORTRAIT -> 0f
            me.magnum.melonds.domain.model.layout.LayoutConfiguration.LayoutOrientation.LANDSCAPE -> 90f
            else -> 0f
        }
    }

    fun setBackground(color: Int) {
        placeholderRenderer.color = color
        surfaceView.requestRender()
    }

    fun showBackground() {
        attachView(surfaceView)
    }

    /**
     * Attach a view to be rendered on the external display instead of the
     * placeholder. This will typically be a MelonView showing the top screen.
     * TODO: redirect the emulator's top screen output here.
     */
    fun attachView(view: View) {
        container.removeAllViews()
        container.addView(view)
        if (touchOverlay != null) {
            // Ensure the touch overlay stays on top of any newly attached view
            container.addView(touchOverlay)
        }
        if (view is GLSurfaceView) {
            surfaceView = view
        }
    }

    /**
     * Replace the placeholder surface with a renderer displaying the emulator's
     * top screen. Returns the created renderer so callers can request renders
     * when new frames are available.
     */
    private fun createSurfaceView(renderer: GLSurfaceView.Renderer): GLSurfaceView {
        return GLSurfaceView(context).apply {
            setEGLContextClientVersion(3)
            sharedContext?.let { setEGLContextFactory(SharedEglContextFactory(it)) }
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }
    }

    fun showDsScreen(screen: DsScreen): DSScreenRenderer {
        val renderer = DSScreenRenderer(screen)
        val view = createSurfaceView(renderer)
        attachView(view)
        return renderer
    }

    /**
     * Display both DS screens using the provided layout rectangles.
     */
    fun showCustomLayout(
        topRect: Rect?,
        bottomRect: Rect?,
        layoutWidth: Int,
        layoutHeight: Int,
    ): DSLayoutRenderer {
        val renderer = DSLayoutRenderer(topRect, bottomRect, layoutWidth, layoutHeight)
        val view = createSurfaceView(renderer)
        attachView(view)
        return renderer
    }

    fun showTopScreen() = showDsScreen(DsScreen.TOP)
    fun showBottomScreen() = showDsScreen(DsScreen.BOTTOM)

    fun requestRender() {
        surfaceView.requestRender()
    }

    fun showAchievements(
        viewModel: RomListRetroAchievementsViewModel,
        isDarkTheme: Boolean,
    ) {
        val composeView = ComposeView(context)
        // Let Compose draw the entire background; otherwise the default view
        // background (typically white) would show through.
        composeView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        composeView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
        composeView.setContent {
            MelonTheme(isDarkTheme = isDarkTheme) {
                val state by viewModel.uiState.collectAsState()
                AchievementList(
                    modifier = Modifier.fillMaxSize(),
                    state = state,
                    onViewAchievement = { viewModel.viewAchievement(it) },
                    onRetry = { viewModel.retryLoadAchievements() },
                    fillScreen = true,
                )
            }
        }
        attachView(composeView)
    }

    /** Enable or disable touch input forwarding for the bottom screen. When
     *  [rect] is provided, touch coordinates are mapped from that region using
     *  the specified layout size. */
    fun configureTouchInput(
        listener: IInputListener?,
        rect: Rect? = null,
        layoutWidth: Int = 0,
        layoutHeight: Int = 0
    ) {
        if (listener == null) {
            touchOverlay?.let { container.removeView(it) }
            touchOverlay = null
            return
        }

        val overlay = touchOverlay ?: View(context).also {
            touchOverlay = it
            container.addView(it)
        }
        overlay.setOnTouchListener(TouchscreenInputHandler(listener))

        overlay.post {
            val params = if (rect != null && layoutWidth > 0 && layoutHeight > 0) {
                val widthScale = container.width.toFloat() / layoutWidth
                val heightScale = container.height.toFloat() / layoutHeight
                FrameLayout.LayoutParams(
                    (rect.width * widthScale).toInt(),
                    (rect.height * heightScale).toInt()
                ).apply {
                    leftMargin = (rect.x * widthScale).toInt()
                    topMargin = (rect.y * heightScale).toInt()
                }
            } else {
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            overlay.layoutParams = params
        }
    }
}
