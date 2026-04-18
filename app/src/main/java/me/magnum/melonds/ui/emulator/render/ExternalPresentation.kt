package me.magnum.melonds.ui.emulator.render

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import me.magnum.melonds.domain.model.RuntimeBackground
import me.magnum.melonds.domain.model.layout.LayoutComponent
import me.magnum.melonds.ui.emulator.DSRenderer
import me.magnum.melonds.ui.emulator.EmulatorSurfaceView
import me.magnum.melonds.ui.emulator.RuntimeLayoutView
import me.magnum.melonds.ui.emulator.model.RuntimeInputLayoutConfiguration
import me.magnum.melonds.ui.emulator.model.RuntimeRendererConfiguration
import me.magnum.melonds.ui.layouteditor.model.LayoutTarget

class ExternalPresentation(
    context: Context,
    display: Display,
    private val frameRenderCoordinator: FrameRenderCoordinator,
) : Presentation(context, display) {

    val layoutView = RuntimeLayoutView(context)
    private val container = FrameLayout(context)
    private val pauseOverlay = View(context)
    private val emulatorRenderer: DSRenderer
    private val surfaceView: EmulatorSurfaceView
    private var currentBackground: RuntimeBackground? = null
    private var currentRendererConfiguration: RuntimeRendererConfiguration? = null

    init {
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        )

        val layoutChangeListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateRendererScreenAreas()
        }

        container.addOnLayoutChangeListener(layoutChangeListener)
        emulatorRenderer = DSRenderer(context).also {
            surfaceView = createSurfaceView(it)
        }
        surfaceView.updateRendererConfiguration(currentRendererConfiguration)

        container.addView(surfaceView)
        container.addView(layoutView)
        container.addView(pauseOverlay)

        pauseOverlay.apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0.6f
            isVisible = false
            setOnClickListener {
                // Do nothing. Just intercept clicks
            }
        }

        frameRenderCoordinator.addSurface(surfaceView)

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

    fun swapScreens() {
        layoutView.swapScreens()
        updateRendererScreenAreas()
    }

    fun updateRendererScreenAreas() {
        val (topScreen, bottomScreen) = if (layoutView.areScreensSwapped()) {
            LayoutComponent.BOTTOM_SCREEN to LayoutComponent.TOP_SCREEN
        } else {
            LayoutComponent.TOP_SCREEN to LayoutComponent.BOTTOM_SCREEN
        }
        val topView = layoutView.getLayoutComponentView(topScreen)
        val bottomView = layoutView.getLayoutComponentView(bottomScreen)
        emulatorRenderer.updateScreenAreas(
            topScreenRect = topView?.getRect(),
            bottomScreenRect = bottomView?.getRect(),
            topAlpha = topView?.baseAlpha ?: 1f,
            bottomAlpha = bottomView?.baseAlpha ?: 1f,
            topOnTop = topView?.onTop ?: false,
            bottomOnTop = bottomView?.onTop ?: false,
        )
    }

    fun setPauseOverlayVisibility(visible: Boolean) {
        pauseOverlay.isVisible = visible
    }

    private fun createSurfaceView(renderer: EmulatorRenderer): EmulatorSurfaceView {
        return EmulatorSurfaceView(context).apply {
            setRenderer(renderer)
            isFocusable = false
            isFocusableInTouchMode = false
        }
    }

    fun updateLayout(layoutConfiguration: RuntimeInputLayoutConfiguration) {
        layoutView.instantiateLayout(layoutConfiguration, LayoutTarget.SECONDARY_SCREEN)
        updateRendererScreenAreas()
    }

    fun updateRendererConfiguration(newRendererConfiguration: RuntimeRendererConfiguration?) {
        currentRendererConfiguration = newRendererConfiguration
        surfaceView.updateRendererConfiguration(newRendererConfiguration)
    }

    override fun onStop() {
        super.onStop()
        frameRenderCoordinator.removeSurface(surfaceView)
    }

    fun updateBackground(background: RuntimeBackground) {
        currentBackground = background
        emulatorRenderer.setBackground(background)
    }
}