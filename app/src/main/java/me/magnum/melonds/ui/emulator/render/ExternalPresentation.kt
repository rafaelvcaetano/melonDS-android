package me.magnum.melonds.ui.emulator.render

import android.app.Presentation
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.Display
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.graphics.toColorInt
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import me.magnum.melonds.domain.model.DsExternalScreen
import me.magnum.melonds.domain.model.Rect
import me.magnum.melonds.domain.model.RuntimeBackground
import me.magnum.melonds.domain.model.consoleAspectRatio
import me.magnum.melonds.ui.emulator.DSRenderer
import me.magnum.melonds.ui.emulator.EmulatorSurfaceView
import me.magnum.melonds.ui.emulator.input.ExternalTouchscreenInputHandler
import me.magnum.melonds.ui.emulator.input.IInputListener
import me.magnum.melonds.ui.emulator.model.ExternalDisplayConfiguration
import me.magnum.melonds.ui.emulator.model.RuntimeRendererConfiguration


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
class ExternalPresentation(
    context: Context,
    display: Display,
    initialDisplayConfiguration: ExternalDisplayConfiguration?,
    areScreensSwapped: Boolean,
    private val frameRenderCoordinator: FrameRenderCoordinator,
    private val inputListener: IInputListener,
) : Presentation(context, display) {

    private val container = FrameLayout(context)
    private var emulatorRenderer: EmulatorRenderer? = null
    private var surfaceView: EmulatorSurfaceView? = null
    private var currentBackground: RuntimeBackground? = null
    private var currentRendererConfiguration: RuntimeRendererConfiguration? = null
    private var currentExternalDisplayConfiguration: ExternalDisplayConfiguration? = null

    init {
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        )
        if (initialDisplayConfiguration == null) {
            val renderer = ColorRenderer("black".toColorInt())
            emulatorRenderer = renderer
            surfaceView = createSurfaceView(renderer).also {
                container.addView(it)
                frameRenderCoordinator.addSurface(it)
            }
        } else {
            updateExternalDisplayConfiguration(initialDisplayConfiguration, areScreensSwapped)
        }

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

    private fun attachView(view: EmulatorSurfaceView) {
        container.removeAllViews()
        surfaceView?.let {
            frameRenderCoordinator.removeSurface(it)
        }
        view.updateRendererConfiguration(currentRendererConfiguration)
        container.addView(view)
        surfaceView = view
        frameRenderCoordinator.addSurface(view)
    }

    private fun createSurfaceView(renderer: EmulatorRenderer): EmulatorSurfaceView {
        return EmulatorSurfaceView(context).apply {
            setRenderer(renderer)
            isFocusable = false
            isFocusableInTouchMode = false
        }
    }

    private fun showDsScreen(screen: DsExternalScreen) {
        val renderer = ExternalScreenRender(
            screen = screen,
            rotateLeft = currentExternalDisplayConfiguration?.rotateLeft ?: false,
            keepAspectRatio = currentExternalDisplayConfiguration?.keepAspectRatio ?: true,
        )
        emulatorRenderer = renderer
        val view = createSurfaceView(renderer)
        if (screen == DsExternalScreen.BOTTOM) {
            val screenAspectRatio = if (currentExternalDisplayConfiguration?.keepAspectRatio == false) {
                null
            } else {
                consoleAspectRatio
            }
            view.setOnTouchListener(ExternalTouchscreenInputHandler(inputListener, screenAspectRatio))
        }
        attachView(view)
    }

    private fun showCustomLayout(
        topRect: Rect?,
        bottomRect: Rect?,
        topAlpha: Float,
        bottomAlpha: Float,
        topOnTop: Boolean,
        bottomOnTop: Boolean,
    ): EmulatorRenderer {
        val renderer = DSRenderer(context).apply {
            updateScreenAreas(topRect, bottomRect, topAlpha, bottomAlpha, topOnTop, bottomOnTop)
            currentBackground?.let {
                setBackground(it)
            }
        }

        emulatorRenderer = renderer
        val view = createSurfaceView(renderer)
        attachView(view)
        return renderer
    }

    fun updateRendererConfiguration(newRendererConfiguration: RuntimeRendererConfiguration?) {
        currentRendererConfiguration = newRendererConfiguration
        surfaceView?.updateRendererConfiguration(newRendererConfiguration)
    }

    fun updateExternalDisplayConfiguration(newExternalDisplayConfiguration: ExternalDisplayConfiguration, areScreensSwapped: Boolean) {
        val oldDisplayMode = currentExternalDisplayConfiguration?.displayMode

        val newDisplayMode = when (newExternalDisplayConfiguration.displayMode) {
            DsExternalScreen.TOP -> if (areScreensSwapped) DsExternalScreen.BOTTOM else DsExternalScreen.TOP
            DsExternalScreen.BOTTOM -> if (areScreensSwapped) DsExternalScreen.TOP else DsExternalScreen.BOTTOM
            DsExternalScreen.CUSTOM -> DsExternalScreen.CUSTOM
        }

        currentExternalDisplayConfiguration = newExternalDisplayConfiguration.copy(displayMode = newDisplayMode)

        if (oldDisplayMode != newDisplayMode) {
            // When the display mode changes, a new renderer will be created, so we don't need to manually apply the new configuration since when creating the new renderer,
            // the new configuration will be used by default
            when (newDisplayMode) {
                DsExternalScreen.TOP -> showDsScreen(DsExternalScreen.TOP)
                DsExternalScreen.BOTTOM -> showDsScreen(DsExternalScreen.BOTTOM)
                DsExternalScreen.CUSTOM -> { /* showCustomLayout() */ }
            }
        } else {
            val screenAspectRatio = if (currentExternalDisplayConfiguration?.keepAspectRatio == false) {
                null
            } else {
                consoleAspectRatio
            }
            surfaceView?.setOnTouchListener(ExternalTouchscreenInputHandler(inputListener, screenAspectRatio))
            emulatorRenderer?.setLeftRotationEnabled(newExternalDisplayConfiguration.rotateLeft)
            (emulatorRenderer as? ExternalScreenRender)?.setKeepAspectRatio(newExternalDisplayConfiguration.keepAspectRatio)
        }
    }

    override fun onStop() {
        super.onStop()
        surfaceView?.let {
            frameRenderCoordinator.removeSurface(it)
        }
    }

    fun updateBackground(background: RuntimeBackground) {
        currentBackground = background
        (emulatorRenderer as? DSRenderer)?.setBackground(background)
    }
}