package me.magnum.melonds.ui

import android.app.Presentation
import android.content.Context
import android.opengl.GLSurfaceView
import me.magnum.melonds.common.opengl.SharedEglContextFactory
import javax.microedition.khronos.egl.EGLContext as EGL10Context
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.core.graphics.toColorInt
import me.magnum.melonds.domain.model.DsExternalScreen
import me.magnum.melonds.domain.model.Rect
import me.magnum.melonds.domain.model.RuntimeBackground
import me.magnum.melonds.ui.emulator.input.IInputListener
import me.magnum.melonds.ui.emulator.input.TouchscreenInputHandler
import androidx.compose.ui.platform.ComposeView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import me.magnum.melonds.ui.emulator.ui.AchievementList
import me.magnum.melonds.ui.romlist.RomListRetroAchievementsViewModel
import me.magnum.melonds.ui.theme.MelonTheme


/**
 * Manages the presentation of content on an external display.
 *
 * This class extends [Presentation] to display emulator screens or custom layouts
 * on a secondary display. It handles the creation and management of [GLSurfaceView]
 * instances for rendering, allowing for shared EGL contexts for efficient OpenGL rendering.
 *
 * The `ExternalPresentation` can display:
 * - A single DS screen (top or bottom).
 * - A custom layout with specified rectangles for the top and bottom screens.
 * - A placeholder color when no specific content is set.
 *
 * It provides methods to switch between these display modes and to request renders
 * on the active [GLSurfaceView].
 *
 * @param context The [Context] in which the presentation is running.
 * @param display The [Display] on which to show the presentation.
 */
class ExternalPresentation(context: Context, display: Display) : Presentation(context, display) {
    private val container = FrameLayout(context)

    private var surfaceView: GLSurfaceView
    private val placeholderRenderer = ColorRenderer("black".toColorInt())
    private var sharedContext: EGL10Context? = null

    init {
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        )
        surfaceView = GLSurfaceView(context).apply {
            setEGLContextClientVersion(3)
            setRenderer(placeholderRenderer)
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
            isFocusable = false
            isFocusableInTouchMode = false
        }
        container.addView(surfaceView)

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

    fun setBackground(color: Int) {
        placeholderRenderer.color = color
        surfaceView.requestRender()
    }

    private fun attachView(view: View) {
        container.removeAllViews()
        container.addView(view)
        if (view is GLSurfaceView) {
            surfaceView = view
        }
    }

    /**
     * Creates and configures a [GLSurfaceView] with the given renderer.
     *
     * This function initializes a [GLSurfaceView] with OpenGL ES 3.0,
     * optionally sets up a shared EGL context if [sharedContext] is available,
     * assigns the provided [renderer], and sets the render mode to
     * [GLSurfaceView.RENDERMODE_WHEN_DIRTY] for efficiency.
     *
     * @param renderer The [GLSurfaceView.Renderer] to be used for rendering.
     * @return The configured [GLSurfaceView] instance.
     */
    private fun createSurfaceView(renderer: GLSurfaceView.Renderer): GLSurfaceView {
        return GLSurfaceView(context).apply {
            setEGLContextClientVersion(3)
            sharedContext?.let { setEGLContextFactory(SharedEglContextFactory(it)) }
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
            isFocusable = false
            isFocusableInTouchMode = false
        }
    }

    /**
     * Replaces the current view with a renderer for a specific DS screen.
     *
     * This function sets up a [GLSurfaceView] with an [ExternalScreenRender]
     * to display either the top or bottom screen of the emulator.
     *
     * @param screen The [DsExternalScreen] to display (either [DsExternalScreen.TOP] or [DsExternalScreen.BOTTOM]).
     * @param inputListener Optional listener that receives touch events when the bottom screen is shown.
     * @return The created [ExternalScreenRender] instance, which callers can use
     *         to request new frame renders.
     */
    private fun showDsScreen(
        screen: DsExternalScreen,
        inputListener: IInputListener? = null,
    ): ExternalScreenRender {
        val renderer = ExternalScreenRender(screen)
        val view = createSurfaceView(renderer)
        if (screen == DsExternalScreen.BOTTOM && inputListener != null) {
            view.setOnTouchListener(TouchscreenInputHandler(inputListener))
        }
        attachView(view)
        return renderer
    }


    /**
     * Replaces the current view with a custom layout renderer.
     *
     * This function sets up a [GLSurfaceView] with an [ExternalLayoutRender]
     * to display a custom layout that can include the top screen, bottom screen, or both,
     * arranged according to the provided rectangles and dimensions.
     *
     * @param topRect The [Rect] defining the position and size of the top screen in the custom layout.
     *                If `null`, the top screen will not be rendered.
     * @param bottomRect The [Rect] defining the position and size of the bottom screen in the custom layout.
     *                   If `null`, the bottom screen will not be rendered.
     * @param layoutWidth The total width of the custom layout.
     * @param layoutHeight The total height of the custom layout.
     * @return The created [ExternalLayoutRender] instance, which callers can use
     *         to request new frame renders.
     */
    fun showCustomLayout(
                         topRect: Rect?,
                         bottomRect: Rect?,
                         topAlpha: Float,
                         bottomAlpha: Float,
                         topOnTop: Boolean,
                         bottomOnTop: Boolean,
                         layoutWidth: Int,
                         layoutHeight: Int,
                         background: RuntimeBackground,

    ): ExternalLayoutRender {
        val renderer = ExternalLayoutRender(
            context,
            topRect,
            bottomRect,
            layoutWidth,
            layoutHeight,
            topAlpha,
            bottomAlpha,
            topOnTop,
            bottomOnTop,
            background,
        )
        val view = createSurfaceView(renderer)
        attachView(view)
        return renderer
    }


    /**
     * Replaces the current view with a Composable that displays a list of achievements.
     *
     * This function sets up a [ComposeView] to show the [AchievementList] Composable.
     * It uses the provided [viewModel] to observe and display the achievement data.
     * The theme (dark or light) is determined by the [isDarkTheme] parameter.
     *
     * @param viewModel The [RomListRetroAchievementsViewModel] used to fetch and manage
     *                  achievement data.
     * @param isDarkTheme A boolean indicating whether to use a dark theme for the UI.
     */
    fun showAchievements(
        viewModel: RomListRetroAchievementsViewModel,
        isDarkTheme: Boolean,
    ) {
        val composeView = ComposeView(context)
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

    fun showTopScreen() = showDsScreen(DsExternalScreen.TOP)
    fun showBottomScreen(inputListener: IInputListener) = showDsScreen(DsExternalScreen.BOTTOM, inputListener)

    fun requestRender() {
        surfaceView.requestRender()
    }

    fun queueEvent(event: () -> Unit) {
        surfaceView.queueEvent(event)
    }
}