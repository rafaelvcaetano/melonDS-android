package me.magnum.melonds.ui.emulator

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.core.view.doOnLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.common.vibration.TouchVibrator
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.Point
import me.magnum.melonds.domain.model.input.SoftInputBehaviour
import me.magnum.melonds.domain.model.layout.LayoutComponent
import me.magnum.melonds.domain.model.ScreenAlignment
import me.magnum.melonds.domain.model.SCREEN_HEIGHT
import me.magnum.melonds.domain.model.SCREEN_WIDTH
import me.magnum.melonds.ui.common.LayoutView
import me.magnum.melonds.ui.emulator.input.ButtonsInputHandler
import me.magnum.melonds.ui.emulator.input.DpadInputHandler
import me.magnum.melonds.ui.emulator.input.FrontendInputHandler
import me.magnum.melonds.ui.emulator.input.IInputListener
import me.magnum.melonds.ui.emulator.input.SingleButtonInputHandler
import me.magnum.melonds.ui.emulator.input.TouchscreenInputHandler
import me.magnum.melonds.ui.emulator.input.view.ToggleableImageView
import me.magnum.melonds.ui.emulator.model.ConnectedControllersState
import me.magnum.melonds.ui.emulator.model.RuntimeInputLayoutConfiguration
import me.magnum.melonds.ui.common.SystemGestureExclusionHelper
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.roundToInt

@AndroidEntryPoint
class RuntimeLayoutView(context: Context, attrs: AttributeSet?) : LayoutView(context, attrs) {

    data class EasyModeConfiguration(
        val screenComponent: LayoutComponent,
        val alignment: ScreenAlignment,
        val integerScale: Boolean,
        val keepAspectRatio: Boolean,
        val fillHeight: Boolean,
        val fillWidth: Boolean,
    )

    @Inject
    lateinit var touchVibrator: TouchVibrator

    private var currentRuntimeLayout: RuntimeInputLayoutConfiguration? = null
    private var frontendInputHandler: IInputListener? = null
    private var systemInputHandler: IInputListener? = null
    private var isSoftInputVisible = true
    private var manualScreensSwapped = false
    private var connectedControllersState: ConnectedControllersState = ConnectedControllersState.NoControllers
    private var easyModeConfiguration: EasyModeConfiguration? = null
    private val gestureExclusionHelper = SystemGestureExclusionHelper()
    private var isGestureExclusionEnabled = true
    private var currentTouchScreenComponent: LayoutComponent = LayoutComponent.BOTTOM_SCREEN
    private var isEasyModeSwapped = false

    fun setFrontendInputHandler(frontendInputHandler: FrontendInputHandler) {
        this.frontendInputHandler = frontendInputHandler
        updateInputs()
    }

    fun setSystemInputHandler(systemInputHandler: IInputListener) {
        this.systemInputHandler = systemInputHandler
        updateInputs()
    }

    fun setConnectedControllersState(state: ConnectedControllersState) {
        connectedControllersState = state
        updateVisibility()
    }

    fun setEasyModeConfiguration(configuration: EasyModeConfiguration?) {
        if (easyModeConfiguration == configuration) {
            return
        }
        isEasyModeSwapped = false
        easyModeConfiguration = configuration
        if (configuration == null) {
            currentRuntimeLayout?.let { instantiateLayout(it) }
        } else {
            applyEasyModeConfiguration()
        }
    }

    fun toggleSoftInputVisibility() {
        isSoftInputVisible = !isSoftInputVisible
        setLayoutComponentToggleState(LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT, isSoftInputVisible)
        updateVisibility()
    }

    fun swapScreens() {
        manualScreensSwapped = !manualScreensSwapped
        if (easyModeConfiguration != null) {
            isEasyModeSwapped = !isEasyModeSwapped
            applyEasyModeConfiguration()
        }
        updateScreenInputs()
    }

    fun areScreensSwapped(): Boolean {
        return manualScreensSwapped
    }

    fun shouldRendererSwapScreens(): Boolean {
        return easyModeConfiguration == null && manualScreensSwapped
    }

    fun setLayoutComponentToggleState(layoutComponent: LayoutComponent, isEnabled: Boolean) {
        val toggleableImageView = getLayoutComponentView(layoutComponent)?.view as? ToggleableImageView ?: return
        toggleableImageView.setToggleState(isEnabled)
    }

    fun instantiateLayout(runtimeLayout: RuntimeInputLayoutConfiguration) {
        currentRuntimeLayout = runtimeLayout
        gestureExclusionHelper.clearAll()
        instantiateLayout(runtimeLayout.layout)
        updateInputs()
        updateVisibility()
        setLayoutComponentToggleState(LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT, isSoftInputVisible)
        applyEasyModeConfiguration()
    }

    private fun updateInputs() {
        val currentRuntimeLayout = currentRuntimeLayout
        if (currentRuntimeLayout == null) {
            isGone = true
            return
        }

        isVisible = true
        val inputAlpha = currentRuntimeLayout.softInputOpacity / 100f

        val enableHapticFeedback = currentRuntimeLayout.isHapticFeedbackEnabled
        systemInputHandler?.let {
            getLayoutComponentView(LayoutComponent.DPAD)?.view?.setOnTouchListener(DpadInputHandler(it, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTONS)?.view?.setOnTouchListener(ButtonsInputHandler(it, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_L)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.L, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_R)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.R, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_SELECT)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.SELECT, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_START)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.START, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_HINGE)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.HINGE, enableHapticFeedback, touchVibrator))
        }
        frontendInputHandler?.let {
            getLayoutComponentView(LayoutComponent.BUTTON_RESET)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.RESET, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_PAUSE)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.PAUSE, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_FAST_FORWARD_TOGGLE)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.FAST_FORWARD, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_MICROPHONE_TOGGLE)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.MICROPHONE, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.TOGGLE_SOFT_INPUT, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_SWAP_SCREENS)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.SWAP_SCREENS, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_QUICK_SAVE)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.QUICK_SAVE, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_QUICK_LOAD)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.QUICK_LOAD, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_REWIND)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.REWIND, enableHapticFeedback, touchVibrator))
        }

        getLayoutComponentViews().forEach {
            if (!it.component.isScreen()) {
                it.view.apply {
                    alpha = inputAlpha
                }
            }
        }

        updateScreenInputs()
    }

    private fun applyEasyModeConfiguration() {
        val configuration = easyModeConfiguration ?: return
        if (!isLaidOut) {
            doOnLayout { applyEasyModeConfiguration() }
            return
        }

        val availableWidth = width
        val availableHeight = height
        if (availableWidth <= 0 || availableHeight <= 0) {
            return
        }

        val otherComponent = if (configuration.screenComponent == LayoutComponent.TOP_SCREEN) {
            LayoutComponent.BOTTOM_SCREEN
        } else {
            LayoutComponent.TOP_SCREEN
        }
        val displayComponent: LayoutComponent
        val hiddenComponent: LayoutComponent
        if (isEasyModeSwapped) {
            displayComponent = otherComponent
            hiddenComponent = configuration.screenComponent
        } else {
            displayComponent = configuration.screenComponent
            hiddenComponent = otherComponent
        }

        val targetView = getLayoutComponentView(displayComponent) ?: return
        val otherView = getLayoutComponentView(hiddenComponent)

        val (baseWidth, baseHeight) = when {
            configuration.integerScale -> computeIntegerScaleDimensions(availableWidth, availableHeight)
            configuration.keepAspectRatio -> computeAspectRatioDimensions(availableWidth, availableHeight)
            else -> availableWidth to availableHeight
        }

        val canFill = configuration.integerScale || configuration.keepAspectRatio
        val scaledWidth = if (canFill && configuration.fillWidth) availableWidth else baseWidth
        val scaledHeight = if (canFill && configuration.fillHeight) availableHeight else baseHeight

        val left = ((availableWidth - scaledWidth) / 2f).roundToInt().coerceAtLeast(0)
        val top = when (configuration.alignment) {
            ScreenAlignment.TOP -> 0
            ScreenAlignment.CENTER -> ((availableHeight - scaledHeight) / 2f).roundToInt().coerceAtLeast(0)
            ScreenAlignment.BOTTOM -> (availableHeight - scaledHeight).coerceAtLeast(0)
        }

        targetView.setPositionAndSize(Point(left, top), scaledWidth, scaledHeight)
        targetView.view.visibility = View.VISIBLE
        targetView.baseAlpha = 1f

        otherView?.apply {
            setPositionAndSize(Point(0, 0), 0, 0)
            view.visibility = View.GONE
            baseAlpha = 0f
        }
        updateScreenInputs()
    }

    private fun computeIntegerScaleDimensions(availableWidth: Int, availableHeight: Int): Pair<Int, Int> {
        val widthScale = availableWidth / SCREEN_WIDTH
        val heightScale = availableHeight / SCREEN_HEIGHT
        val maxIntegerScale = min(widthScale, heightScale)
        val scale = if (maxIntegerScale <= 0) {
            min(
                availableWidth.toFloat() / SCREEN_WIDTH,
                availableHeight.toFloat() / SCREEN_HEIGHT,
            )
        } else {
            maxIntegerScale.toFloat()
        }
        val width = (SCREEN_WIDTH * scale).roundToInt().coerceAtLeast(1).coerceAtMost(availableWidth)
        val height = (SCREEN_HEIGHT * scale).roundToInt().coerceAtLeast(1).coerceAtMost(availableHeight)
        return width to height
    }

    private fun computeAspectRatioDimensions(availableWidth: Int, availableHeight: Int): Pair<Int, Int> {
        val widthRatio = availableWidth.toFloat() / SCREEN_WIDTH
        val heightRatio = availableHeight.toFloat() / SCREEN_HEIGHT
        val scale = min(widthRatio, heightRatio)
        val width = (SCREEN_WIDTH * scale).roundToInt().coerceAtLeast(1).coerceAtMost(availableWidth)
        val height = (SCREEN_HEIGHT * scale).roundToInt().coerceAtLeast(1).coerceAtMost(availableHeight)
        return width to height
    }

    private fun updateScreenInputs() {
        val (touchScreenComponent, nonTouchScreenComponent) = if (shouldRendererSwapScreens()) {
            LayoutComponent.TOP_SCREEN to LayoutComponent.BOTTOM_SCREEN
        } else {
            LayoutComponent.BOTTOM_SCREEN to LayoutComponent.TOP_SCREEN
        }
        currentTouchScreenComponent = touchScreenComponent
        systemInputHandler?.let {
            getLayoutComponentView(touchScreenComponent)?.view?.setOnTouchListener(TouchscreenInputHandler(it))
        }
        getLayoutComponentView(nonTouchScreenComponent)?.view?.setOnTouchListener(null)

        applyGestureExclusion(touchScreenComponent, nonTouchScreenComponent)
    }

    fun isTouchScreenVisible(): Boolean {
        val view = getLayoutComponentView(currentTouchScreenComponent)?.view ?: return false
        val params = view.layoutParams
        val width = params?.width ?: view.width
        val height = params?.height ?: view.height
        return view.visibility == View.VISIBLE && width > 0 && height > 0
    }

    private fun updateVisibility() {
        val currentConnectedControllersState = connectedControllersState
        var hiddenComponents = when(currentRuntimeLayout?.softInputBehaviour) {
            SoftInputBehaviour.ALWAYS_VISIBLE -> emptyList()
            SoftInputBehaviour.HIDE_SYSTEM_BUTTONS_WHEN_CONTROLLERS_CONNECTED, null -> {
                when(currentConnectedControllersState) {
                    ConnectedControllersState.NoControllers -> emptyList()
                    is ConnectedControllersState.ControllersConnected -> listOf(
                        LayoutComponent.BUTTONS,
                        LayoutComponent.DPAD,
                        LayoutComponent.BUTTON_L,
                        LayoutComponent.BUTTON_R,
                        LayoutComponent.BUTTON_START,
                        LayoutComponent.BUTTON_SELECT
                    )
                }
            }
            SoftInputBehaviour.HIDE_ALL_BUTTONS_ASSIGNED_TO_CONNECTED_CONTROLLERS -> when(currentConnectedControllersState) {
                ConnectedControllersState.NoControllers -> emptyList()
                is ConnectedControllersState.ControllersConnected -> {
                    LayoutComponent.entries.filter {
                        // The component can be hidden if all matching inputs are assigned to connected controllers
                        it.matchingInputs.all {
                            currentConnectedControllersState.assignedInputs.contains(it)
                        }
                    }
                }
            }
            SoftInputBehaviour.ALWAYS_INVISIBLE -> LayoutComponent.entries.toList()
        }

        if (!isSoftInputVisible) {
            // Hide everything except the soft input toggle button if it was not already hidden by the soft input behaviour logic
            hiddenComponents = if (hiddenComponents.contains(LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT)) {
                LayoutComponent.entries.toList()
            } else {
                LayoutComponent.entries.toList().filter { it != LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT}
            }
        }

        val visibleComponents = LayoutComponent.entries.filter { !hiddenComponents.contains(it) }

        hiddenComponents.forEach { component ->
            if (!component.isScreen()) {
                getLayoutComponentView(component)?.view?.isVisible = false
            }
        }
        visibleComponents.forEach { component ->
            if (!component.isScreen()) {
                getLayoutComponentView(component)?.view?.isVisible = true
            }
        }
    }

    fun setTouchScreenGestureExclusionEnabled(enabled: Boolean) {
        if (isGestureExclusionEnabled == enabled) {
            return
        }
        isGestureExclusionEnabled = enabled
        updateScreenInputs()
    }

    fun destroyRuntimeLayout() {
        gestureExclusionHelper.clearAll()
        super.destroyLayout()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        gestureExclusionHelper.clearAll()
    }

    private fun applyGestureExclusion(
        touchScreenComponent: LayoutComponent,
        nonTouchScreenComponent: LayoutComponent
    ) {
        if (isGestureExclusionEnabled) {
            setGestureExclusionForComponent(touchScreenComponent, true)
            setGestureExclusionForComponent(nonTouchScreenComponent, false)
        } else {
            setGestureExclusionForComponent(touchScreenComponent, false)
            setGestureExclusionForComponent(nonTouchScreenComponent, false)
        }
    }

    private fun setGestureExclusionForComponent(component: LayoutComponent, enabled: Boolean) {
        if (!component.isScreen()) {
            return
        }
        val view = getLayoutComponentView(component)?.view
        gestureExclusionHelper.setGestureExclusion(view, enabled)
    }
}
