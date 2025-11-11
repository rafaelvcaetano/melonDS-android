package me.magnum.melonds.ui.emulator.render

import android.app.Presentation
import android.content.Context
import android.graphics.RectF
import android.opengl.GLSurfaceView
import android.view.Display
import android.view.View
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
import me.magnum.melonds.domain.model.Point
import me.magnum.melonds.domain.model.Rect
import me.magnum.melonds.domain.model.RuntimeBackground
import me.magnum.melonds.domain.model.ScreenAlignment
import me.magnum.melonds.domain.model.consoleAspectRatio
import me.magnum.melonds.domain.model.SCREEN_WIDTH
import me.magnum.melonds.domain.model.SCREEN_HEIGHT
import me.magnum.melonds.domain.model.layout.LayoutComponent
import me.magnum.melonds.domain.model.layout.ScreenFold
import me.magnum.melonds.domain.model.layout.UILayout
import me.magnum.melonds.domain.model.layout.UILayoutVariant
import me.magnum.melonds.domain.model.ui.Orientation
import me.magnum.melonds.impl.DefaultLayoutProvider
import me.magnum.melonds.impl.ScreenUnitsConverter
import me.magnum.melonds.ui.emulator.DSRenderer
import me.magnum.melonds.ui.emulator.EmulatorSurfaceView
import me.magnum.melonds.ui.emulator.input.ExternalTouchscreenInputHandler
import me.magnum.melonds.ui.emulator.input.IInputListener
import me.magnum.melonds.ui.emulator.model.ExternalDisplayConfiguration
import me.magnum.melonds.ui.emulator.model.ExternalLayoutState
import me.magnum.melonds.ui.emulator.model.RuntimeRendererConfiguration
import me.magnum.melonds.ui.common.SystemGestureExclusionHelper
import kotlin.math.min
import kotlin.math.roundToInt


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
    private val gestureExclusionHelper = SystemGestureExclusionHelper()
    private var isGestureHost = false
    private var currentCustomLayoutState: ExternalLayoutState? = null
    private var customLayoutRenderer: ExternalLayoutRender? = null
    private var currentScreensSwapped = false
    private val defaultLayoutProvider = DefaultLayoutProvider(ScreenUnitsConverter(context))

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

    fun setBottomScreenGestureHost(isHost: Boolean) {
        if (isGestureHost == isHost) {
            return
        }
        isGestureHost = isHost
        applyGestureExclusion()
    }

    private fun attachView(view: EmulatorSurfaceView) {
        container.removeAllViews()
        surfaceView?.let {
            gestureExclusionHelper.setGestureExclusion(it, false)
            frameRenderCoordinator.removeSurface(it)
        }
        view.updateRendererConfiguration(currentRendererConfiguration)
        container.addView(view)
        surfaceView = view
        frameRenderCoordinator.addSurface(view)
        applyGestureExclusion()
    }

    private fun createSurfaceView(renderer: EmulatorRenderer): EmulatorSurfaceView {
        return EmulatorSurfaceView(context).apply {
            setRenderer(renderer)
            isFocusable = false
            isFocusableInTouchMode = false
        }
    }

    private fun showDsScreen(screen: DsExternalScreen) {
        customLayoutRenderer = null
        val renderer = ExternalScreenRender(
            screen = screen,
            rotateLeft = currentExternalDisplayConfiguration?.rotateLeft ?: false,
            keepAspectRatio = currentExternalDisplayConfiguration?.keepAspectRatio ?: true,
            integerScale = currentExternalDisplayConfiguration?.integerScale ?: false,
            verticalAlignment = currentExternalDisplayConfiguration?.verticalAlignment ?: ScreenAlignment.TOP,
            fillHeight = currentExternalDisplayConfiguration?.fillHeight ?: false,
            fillWidth = currentExternalDisplayConfiguration?.fillWidth ?: false,
        )
        emulatorRenderer = renderer
        val view = createSurfaceView(renderer)
        val isBottomScreen = screen == DsExternalScreen.BOTTOM
        if (isBottomScreen) {
            val viewportProvider = {
                computeBottomScreenViewport(view.width, view.height)
            }
            view.setOnTouchListener(ExternalTouchscreenInputHandler(inputListener, viewportProvider))
        } else {
            view.setOnTouchListener(null)
        }
        attachView(view)
        gestureExclusionHelper.setGestureExclusion(view, isBottomScreen)
    }

    private fun createCustomLayoutRenderer(): ExternalLayoutRender {
        val renderer = ExternalLayoutRender(
            context = context,
            topScreen = null,
            bottomScreen = null,
            layoutWidth = 1,
            layoutHeight = 1,
            topAlpha = 1f,
            bottomAlpha = 1f,
            topOnTop = false,
            bottomOnTop = false,
            background = currentBackground ?: RuntimeBackground.None,
            rotateLeft = currentExternalDisplayConfiguration?.rotateLeft ?: false,
        )
        emulatorRenderer = renderer
        customLayoutRenderer = renderer
        val view = createSurfaceView(renderer)
        attachView(view)
        return renderer
    }

    fun updateRendererConfiguration(newRendererConfiguration: RuntimeRendererConfiguration?) {
        currentRendererConfiguration = newRendererConfiguration
        surfaceView?.updateRendererConfiguration(newRendererConfiguration)
    }

    private fun applyCustomLayoutState(state: ExternalLayoutState) {
        val renderer = customLayoutRenderer ?: createCustomLayoutRenderer()
        val topState = if (currentScreensSwapped) state.bottomScreen else state.topScreen
        val bottomState = if (currentScreensSwapped) state.topScreen else state.bottomScreen

        renderer.updateLayout(
            topState?.rect,
            bottomState?.rect,
            state.layoutWidth,
            state.layoutHeight,
            topState?.alpha ?: 0f,
            bottomState?.alpha ?: 0f,
            topState?.onTop ?: false,
            bottomState?.onTop ?: false,
        )
        updateCustomTouchHandler(renderer, bottomState != null)
    }

    private fun updateCustomTouchHandler(renderer: ExternalLayoutRender, hasBottomScreen: Boolean) {
        val view = surfaceView ?: return
        if (hasBottomScreen) {
            val viewportProvider = {
                renderer.getBottomViewport(view.width, view.height)
            }
            view.setOnTouchListener(ExternalTouchscreenInputHandler(inputListener, viewportProvider))
        } else {
            view.setOnTouchListener(null)
        }
        applyGestureExclusion()
    }

    private fun applyDefaultLayoutOrDefer() {
        val renderer = customLayoutRenderer ?: createCustomLayoutRenderer()
        if (!applyDefaultLayout(renderer)) {
            val view = surfaceView ?: return
            val listener = object : View.OnLayoutChangeListener {
                override fun onLayoutChange(
                    v: View?,
                    left: Int,
                    top: Int,
                    right: Int,
                    bottom: Int,
                    oldLeft: Int,
                    oldTop: Int,
                    oldRight: Int,
                    oldBottom: Int,
                ) {
                    if (applyDefaultLayout(renderer)) {
                        v?.removeOnLayoutChangeListener(this)
                    }
                }
            }
            view.addOnLayoutChangeListener(listener)
        }
    }

    private fun applyDefaultLayout(renderer: ExternalLayoutRender): Boolean {
        val view = surfaceView ?: return false
        val width = view.width.takeIf { it > 0 } ?: container.width.takeIf { it > 0 } ?: return false
        val height = view.height.takeIf { it > 0 } ?: container.height.takeIf { it > 0 } ?: return false
        val defaultState = buildDefaultLayoutState(width, height) ?: return false
        renderer.updateLayout(
            top = defaultState.topScreen?.rect,
            bottom = defaultState.bottomScreen?.rect,
            width = defaultState.layoutWidth,
            height = defaultState.layoutHeight,
            topA = defaultState.topScreen?.alpha ?: 0f,
            bottomA = defaultState.bottomScreen?.alpha ?: 0f,
            topOnT = defaultState.topScreen?.onTop ?: false,
            bottomOnT = defaultState.bottomScreen?.onTop ?: false,
        )
        updateCustomTouchHandler(renderer, defaultState.bottomScreen != null)
        return true
    }

    private fun buildDefaultLayoutState(width: Int, height: Int): ExternalLayoutState? {
        if (width <= 0 || height <= 0) return null
        val orientation = if (width >= height) Orientation.LANDSCAPE else Orientation.PORTRAIT
        val layout = defaultLayoutProvider.buildDefaultLayout(width, height, orientation, emptyList<ScreenFold>())
        return layoutToExternalState(layout, width, height)
    }

    private fun layoutToExternalState(layout: UILayout, width: Int, height: Int): ExternalLayoutState? {
        val components = layout.components ?: return null
        val top = components.firstOrNull { it.component == LayoutComponent.TOP_SCREEN }
        val bottom = components.firstOrNull { it.component == LayoutComponent.BOTTOM_SCREEN }
        if (top == null && bottom == null) {
            return null
        }
        return ExternalLayoutState(
            layoutWidth = width,
            layoutHeight = height,
            topScreen = top?.let { ExternalLayoutState.Screen(it.rect, it.alpha, it.onTop) },
            bottomScreen = bottom?.let { ExternalLayoutState.Screen(it.rect, it.alpha, it.onTop) },
        )
    }

    fun updateCustomLayout(state: ExternalLayoutState?) {
        currentCustomLayoutState = state
        if (currentExternalDisplayConfiguration?.displayMode == DsExternalScreen.CUSTOM) {
            if (state == null) {
                applyDefaultLayoutOrDefer()
            } else {
                applyCustomLayoutState(state)
            }
        }
    }

    fun updateExternalDisplayConfiguration(newExternalDisplayConfiguration: ExternalDisplayConfiguration, areScreensSwapped: Boolean) {
        val oldDisplayMode = currentExternalDisplayConfiguration?.displayMode

        val newDisplayMode = when (newExternalDisplayConfiguration.displayMode) {
            DsExternalScreen.TOP -> if (areScreensSwapped) DsExternalScreen.BOTTOM else DsExternalScreen.TOP
            DsExternalScreen.BOTTOM -> if (areScreensSwapped) DsExternalScreen.TOP else DsExternalScreen.BOTTOM
            DsExternalScreen.CUSTOM -> DsExternalScreen.CUSTOM
        }

        currentExternalDisplayConfiguration = newExternalDisplayConfiguration.copy(displayMode = newDisplayMode)
        currentScreensSwapped = areScreensSwapped

        if (oldDisplayMode != newDisplayMode) {
            if (newDisplayMode != DsExternalScreen.CUSTOM) {
                customLayoutRenderer = null
            }
            // When the display mode changes, a new renderer will be created, so we don't need to manually apply the new configuration since when creating the new renderer,
            // the new configuration will be used by default
            when (newDisplayMode) {
                DsExternalScreen.TOP -> showDsScreen(DsExternalScreen.TOP)
                DsExternalScreen.BOTTOM -> showDsScreen(DsExternalScreen.BOTTOM)
                DsExternalScreen.CUSTOM -> {
                    val layoutState = currentCustomLayoutState
                    if (layoutState != null) {
                        applyCustomLayoutState(layoutState)
                    } else {
                        applyDefaultLayoutOrDefer()
                    }
                }
            }
        } else {
            if (currentExternalDisplayConfiguration?.displayMode == DsExternalScreen.BOTTOM) {
                val provider = {
                    val view = surfaceView
                    if (view == null) null else computeBottomScreenViewport(view.width, view.height)
                }
                surfaceView?.setOnTouchListener(ExternalTouchscreenInputHandler(inputListener, provider))
            } else {
                if (currentExternalDisplayConfiguration?.displayMode != DsExternalScreen.CUSTOM) {
                    surfaceView?.setOnTouchListener(null)
                }
            }
            emulatorRenderer?.setLeftRotationEnabled(newExternalDisplayConfiguration.rotateLeft)
            when (val renderer = emulatorRenderer) {
                is ExternalScreenRender -> {
                    renderer.setKeepAspectRatio(newExternalDisplayConfiguration.keepAspectRatio)
                    renderer.setIntegerScale(newExternalDisplayConfiguration.integerScale)
                    renderer.setVerticalAlignment(newExternalDisplayConfiguration.verticalAlignment)
                    renderer.setFillHeight(newExternalDisplayConfiguration.fillHeight)
                    renderer.setFillWidth(newExternalDisplayConfiguration.fillWidth)
                }
                is ExternalLayoutRender -> {
                    renderer.setLeftRotationEnabled(newExternalDisplayConfiguration.rotateLeft)
                }
            }
            if (currentExternalDisplayConfiguration?.displayMode == DsExternalScreen.CUSTOM) {
                val layoutState = currentCustomLayoutState
                if (layoutState != null) {
                    applyCustomLayoutState(layoutState)
                } else {
                    applyDefaultLayoutOrDefer()
                }
            }
        }
        applyGestureExclusion()
    }

    override fun onStop() {
        super.onStop()
        surfaceView?.let {
            frameRenderCoordinator.removeSurface(it)
        }
        gestureExclusionHelper.clearAll()
    }

    fun updateBackground(background: RuntimeBackground) {
        currentBackground = background
        when (val renderer = emulatorRenderer) {
            is DSRenderer -> renderer.setBackground(background)
            is ExternalLayoutRender -> renderer.setBackground(background)
        }
    }

    private fun applyGestureExclusion() {
        val displayMode = currentExternalDisplayConfiguration?.displayMode
        val enable = when (displayMode) {
            DsExternalScreen.BOTTOM -> isGestureHost
            DsExternalScreen.CUSTOM -> isGestureHost && (customLayoutRenderer?.hasBottomScreen() == true)
            else -> false
        }
        gestureExclusionHelper.setGestureExclusion(surfaceView, enable)
    }

    private fun computeBottomScreenViewport(viewWidth: Int, viewHeight: Int): RectF? {
        if (viewWidth <= 0 || viewHeight <= 0) {
            return null
        }
        val config = currentExternalDisplayConfiguration ?: return null
        if (config.displayMode != DsExternalScreen.BOTTOM || config.rotateLeft) {
            // Rotation not supported for touch mapping. Fallback to full view.
            return RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        }

        if (!config.integerScale && !config.keepAspectRatio) {
            return RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        }

        val (baseWidth, baseHeight) = if (config.integerScale) {
            computeIntegerScaleDimensions(viewWidth, viewHeight)
        } else {
            computeAspectDimensions(viewWidth, viewHeight)
        }

        val width = (if ((config.integerScale || config.keepAspectRatio) && config.fillWidth) viewWidth else baseWidth)
            .coerceAtLeast(1)
            .coerceAtMost(viewWidth)
        val height = (if ((config.integerScale || config.keepAspectRatio) && config.fillHeight) viewHeight else baseHeight)
            .coerceAtLeast(1)
            .coerceAtMost(viewHeight)

        val offsetX = ((viewWidth - width) / 2f).coerceAtLeast(0f)
        val extraHeight = (viewHeight - height).coerceAtLeast(0)
        val offsetY = when (config.verticalAlignment) {
            ScreenAlignment.TOP -> 0f
            ScreenAlignment.CENTER -> (extraHeight / 2f).coerceAtLeast(0f)
            ScreenAlignment.BOTTOM -> extraHeight.toFloat()
        }

        return RectF(
            offsetX,
            offsetY,
            offsetX + width,
            offsetY + height,
        )
    }

    private fun computeIntegerScaleDimensions(viewWidth: Int, viewHeight: Int): Pair<Int, Int> {
        val widthScale = viewWidth / SCREEN_WIDTH
        val heightScale = viewHeight / SCREEN_HEIGHT
        val maxScale = min(widthScale, heightScale).coerceAtLeast(1)
        val width = SCREEN_WIDTH * maxScale
        val height = SCREEN_HEIGHT * maxScale
        return width to height
    }

    private fun computeAspectDimensions(viewWidth: Int, viewHeight: Int): Pair<Int, Int> {
        val viewRatio = viewWidth.toFloat() / viewHeight.toFloat()
        return if (viewRatio > consoleAspectRatio) {
            val height = viewHeight
            val width = (height * consoleAspectRatio).roundToInt().coerceAtLeast(1).coerceAtMost(viewWidth)
            width to height
        } else {
            val width = viewWidth
            val height = (width / consoleAspectRatio).roundToInt().coerceAtLeast(1).coerceAtMost(viewHeight)
            width to height
        }
    }
}
