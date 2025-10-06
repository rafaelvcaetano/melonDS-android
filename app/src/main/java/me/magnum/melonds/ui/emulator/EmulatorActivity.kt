package me.magnum.melonds.ui.emulator

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.Choreographer
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.os.ConfigurationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.R
import me.magnum.melonds.common.PermissionHandler
import me.magnum.melonds.databinding.ActivityEmulatorBinding
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.DsExternalScreen
import me.magnum.melonds.domain.model.FpsCounterPosition
import me.magnum.melonds.domain.model.Rect
import me.magnum.melonds.domain.model.SaveStateSlot
import me.magnum.melonds.domain.model.VideoFiltering
import me.magnum.melonds.domain.model.layout.LayoutComponent
import me.magnum.melonds.domain.model.layout.ScreenFold
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.domain.model.ui.Orientation
import me.magnum.melonds.domain.repositories.LayoutsRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.extensions.insetsControllerCompat
import me.magnum.melonds.extensions.parcelable
import me.magnum.melonds.extensions.setLayoutOrientation
import me.magnum.melonds.impl.DefaultLayoutProvider
import me.magnum.melonds.impl.emulator.LifecycleOwnerProvider
import me.magnum.melonds.impl.system.AppForegroundStateObserver
import me.magnum.melonds.parcelables.RomInfoParcelable
import me.magnum.melonds.parcelables.RomParcelable
import me.magnum.melonds.ui.ExternalLayoutRender
import me.magnum.melonds.ui.ExternalPresentation
import me.magnum.melonds.ui.ExternalRenderer
import me.magnum.melonds.ui.ExternalScreenRender
import me.magnum.melonds.ui.cheats.CheatsActivity
import me.magnum.melonds.ui.emulator.component.EmulatorOverlayTracker
import me.magnum.melonds.ui.emulator.input.FrontendInputHandler
import me.magnum.melonds.ui.emulator.input.INativeInputListener
import me.magnum.melonds.ui.emulator.input.InputProcessor
import me.magnum.melonds.ui.emulator.input.MelonTouchHandler
import me.magnum.melonds.ui.emulator.model.EmulatorOverlay
import me.magnum.melonds.ui.emulator.model.EmulatorState
import me.magnum.melonds.ui.emulator.model.EmulatorUiEvent
import me.magnum.melonds.ui.emulator.model.PauseMenu
import me.magnum.melonds.ui.emulator.model.PopupEvent
import me.magnum.melonds.ui.emulator.model.RuntimeInputLayoutConfiguration
import me.magnum.melonds.ui.emulator.model.ToastEvent
import me.magnum.melonds.ui.emulator.rewind.EdgeSpacingDecorator
import me.magnum.melonds.ui.emulator.rewind.RewindSaveStateAdapter
import me.magnum.melonds.ui.emulator.rewind.model.RewindWindow
import me.magnum.melonds.ui.emulator.rom.SaveStateAdapter
import me.magnum.melonds.ui.emulator.ui.AchievementListDialog
import me.magnum.melonds.ui.emulator.ui.AchievementPopupUi
import me.magnum.melonds.ui.emulator.ui.QuickSettingsDialog
import me.magnum.melonds.ui.emulator.ui.RAIntegrationEventUi
import me.magnum.melonds.ui.layouts.ExternalLayoutListActivity
import me.magnum.melonds.ui.layouts.LayoutListActivity
import me.magnum.melonds.ui.settings.SettingsActivity
import me.magnum.melonds.ui.theme.MelonTheme
import java.text.SimpleDateFormat
import javax.inject.Inject

@AndroidEntryPoint
class EmulatorActivity : AppCompatActivity(), Choreographer.FrameCallback {
    companion object {
        const val KEY_ROM = "rom"
        const val KEY_PATH = "PATH"
        const val KEY_URI = "uri"
        const val KEY_BOOT_FIRMWARE_CONSOLE = "boot_firmware_console"
        const val KEY_BOOT_FIRMWARE_ONLY = "boot_firmware_only"

        fun getRomEmulatorActivityIntent(context: Context, rom: Rom): Intent {
            return Intent(context, EmulatorActivity::class.java).apply {
                putExtra(KEY_ROM, RomParcelable(rom))
            }
        }

        fun getFirmwareEmulatorActivityIntent(context: Context, consoleType: ConsoleType): Intent {
            return Intent(context, EmulatorActivity::class.java).apply {
                putExtra(KEY_BOOT_FIRMWARE_ONLY, true)
                putExtra(KEY_BOOT_FIRMWARE_CONSOLE, consoleType.ordinal)
            }
        }
    }

    private lateinit var binding: ActivityEmulatorBinding
    val viewModel: EmulatorViewModel by viewModels()

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var picasso: Picasso

    @Inject
    lateinit var permissionHandler: PermissionHandler

    @Inject
    lateinit var lifecycleOwnerProvider: LifecycleOwnerProvider

    @Inject
    lateinit var appForegroundStateObserver: AppForegroundStateObserver

    /**
     * Repository for managing and accessing layout configurations.
     * This is injected to allow the activity to retrieve layout information.
     */
    @Inject
    lateinit var layoutsRepository: LayoutsRepository

    @Inject
    lateinit var defaultLayoutProvider: DefaultLayoutProvider

    private var presentation: ExternalPresentation? = null

    /**
     * Renderer for displaying the top screen on an external display.
     * This is nullable because it's only initialized if an external display is connected.
     */
    private var externalScreenRender: ExternalRenderer? = null

    /**
     * The current configuration for the external display screen.
     * This is used to determine which screen (top or bottom) is shown on an external display.
     * It's updated when an external display is connected or disconnected, or when the
     * user changes the external display screen setting.
     */
    private var currentExternalDisplayScreen: DsExternalScreen? = null

    private lateinit var displayManager: DisplayManager
    private val displayListener = object : DisplayManager.DisplayListener {
        /**
         * Called when a new display is added to the system.
         *
         * This method is part of the [DisplayManager.DisplayListener] interface.
         * It's triggered when an external display (e.g., via HDMI or screen mirroring)
         * is connected.
         *
         * Upon a new display being added, this method will:
         * 1. Attempt to show the [ExternalPresentation] on the newly added display.
         * 2. Set up the external screen rendering, which might involve configuring
         *    which part of the emulated DS screen (top, bottom, or custom layout)
         *    is shown on the external display.
         *
         * This is executed on the UI thread to ensure thread safety when interacting
         * with UI components and the display manager.
         *
         * @param displayId The ID of the newly added display.
         */
        override fun onDisplayAdded(displayId: Int) {
            runOnUiThread {
                showExternalDisplay()
                setupExternalScreen(true)
            }
        }

        /**
         * Called when a display is removed.
         *
         * This method handles the case where an external display is disconnected.
         * It checks if the removed display is the one currently used for presentation.
         * If so, it dismisses the presentation, nullifies the presentation object
         * in [ExternalDisplayManager], and clears the associated external screen
         * renderer and current external display screen configuration.
         *
         * @param displayId The ID of the display that was removed.
         */
        override fun onDisplayRemoved(displayId: Int) {
            runOnUiThread {
                presentation?.let { pres ->
                    if (pres.display.displayId == displayId) {
                        pres.dismiss()
                        presentation = null
                        externalScreenRender = null
                        currentExternalDisplayScreen = null
                    }
                }
            }
        }

        override fun onDisplayChanged(displayId: Int) {
            // No-op
        }
    }

    private val currentOpenGlContext = MutableStateFlow<Long?>(null)
    private lateinit var dsRenderer: DSRenderer
    private lateinit var melonTouchHandler: MelonTouchHandler
    private lateinit var nativeInputListener: INativeInputListener
    private val frontendInputHandler = object : FrontendInputHandler() {
        var fastForwardEnabled = false
            private set
        var microphoneEnabled = true
            private set

        override fun onSoftInputTogglePressed() {
            binding.viewLayoutControls.toggleSoftInputVisibility()
        }

        override fun onPausePressed() {
            viewModel.pauseEmulator(true)
        }

        override fun onFastForwardPressed() {
            fastForwardEnabled = !fastForwardEnabled
            binding.viewLayoutControls.setLayoutComponentToggleState(LayoutComponent.BUTTON_FAST_FORWARD_TOGGLE, fastForwardEnabled)
            MelonEmulator.setFastForwardEnabled(fastForwardEnabled)
        }

        override fun onMicrophonePressed() {
            microphoneEnabled = !microphoneEnabled
            binding.viewLayoutControls.setLayoutComponentToggleState(LayoutComponent.BUTTON_MICROPHONE_TOGGLE, microphoneEnabled)
            MelonEmulator.setMicrophoneEnabled(microphoneEnabled)
        }

        override fun onResetPressed() {
            viewModel.resetEmulator()
        }

        override fun onSwapScreens() {
            swapScreen()
        }

        override fun onQuickSave() {
            viewModel.doQuickSave()
        }

        override fun onQuickLoad() {
            viewModel.doQuickLoad()
        }

        override fun onRewind() {
            viewModel.onOpenRewind()
        }

        override fun onRefreshExternalScreen() {
            setupExternalScreen(true)
        }
    }
    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onSettingsChanged()
        setupInputHandling()
        setupSustainedPerformanceMode()
        setupFpsCounter()
        viewModel.resumeEmulator()
    }
    private val cheatsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onCheatsChanged()
        viewModel.resumeEmulator()
    }
    private val permissionRequestLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        lifecycleScope.launch {
            it.keys.forEach { permission ->
                permissionHandler.notifyPermissionStatusUpdated(permission)
            }
        }
    }
    private val backPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            handleBackPressed()
        }
    }

    private val rewindSaveStateAdapter = RewindSaveStateAdapter {
        viewModel.rewindToState(it)
        closeRewindWindow()
    }
    private val showAchievementList = mutableStateOf(false)
    private val showQuickSettings = mutableStateOf(false)

    private var emulatorReady = false

    private val activeOverlays = EmulatorOverlayTracker(
        onOverlaysCleared = {
            disableScreenTimeOut()
        },
        onOverlaysPresent = {
            enableScreenTimeOut()
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleOwnerProvider.setCurrentLifecycleOwner(this)
        binding = ActivityEmulatorBinding.inflate(layoutInflater)
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(binding.root)
        setupFullscreen()

        onBackPressedDispatcher.addCallback(backPressedCallback)

        melonTouchHandler = MelonTouchHandler()
        dsRenderer = DSRenderer(this)
        dsRenderer.setOnFrameRenderedListener(viewModel::onFrameRendered)
        binding.surfaceMain.apply {
            setRenderer(dsRenderer)
            setOnGlContextReadyListener {
                currentOpenGlContext.value = it
            }
        }

        displayManager = getSystemService<DisplayManager>()!!
        displayManager.registerDisplayListener(displayListener, null)

        binding.textFps.visibility = View.INVISIBLE
        binding.viewLayoutControls.setLayoutComponentViewBuilderFactory(RuntimeLayoutComponentViewBuilderFactory())
        binding.layoutRewind.setOnClickListener {
            closeRewindWindow()
        }
        binding.listRewind.apply {
            val listLayoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, true)
            layoutManager = listLayoutManager
            addItemDecoration(EdgeSpacingDecorator())
            adapter = rewindSaveStateAdapter
        }
        binding.viewLayoutControls.apply {
            setFrontendInputHandler(frontendInputHandler)
            setSystemInputHandler(melonTouchHandler)
        }

        val layoutChangeListener = View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            updateRendererScreenAreas()

            val oldWith = oldRight - oldLeft
            val oldHeight = oldBottom - oldTop

            val newWidth = right - left
            val newHeight = bottom - top

            if (newWidth != oldWith || newHeight != oldHeight) {
                viewModel.setUiSize(newWidth, newHeight)
            }
        }
        binding.root.addOnLayoutChangeListener(layoutChangeListener)

        setupInputHandling()
        updateOrientation(resources.configuration)
        launchEmulator()

        binding.layoutAchievement.setContent {
            MelonTheme {
                var popupEvent by remember {
                    mutableStateOf<PopupEvent?>(null)
                }
                var popupOffset by remember {
                    mutableStateOf(-1f)
                }
                var popupHeight by remember {
                    mutableStateOf<Int?>(null)
                }

                LaunchedEffect(null) {
                    val achievementsFlow = viewModel.achievementTriggeredEvent.map { PopupEvent.AchievementUnlockPopup(it) }
                    val integrationFlow = viewModel.integrationEvent.map { PopupEvent.RAIntegrationPopup(it) }

                    merge(achievementsFlow, integrationFlow).collect {
                        popupEvent = it
                        animate(
                            initialValue = -1f,
                            targetValue = 0f,
                            animationSpec = tween(easing = LinearEasing),
                        ) { value, _ ->
                            popupOffset = value
                        }
                        delay(5500)
                        animate(
                            initialValue = 0f,
                            targetValue = -1f,
                            animationSpec = tween(easing = LinearEasing),
                        ) { value, _ ->
                            popupOffset = value
                        }
                        popupEvent = null
                    }
                }

                Box(Modifier.fillMaxWidth()) {
                    val currentPopupEvent = popupEvent
                    when (currentPopupEvent) {
                        is PopupEvent.AchievementUnlockPopup -> {
                            AchievementPopupUi(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .offset {
                                        val y = (popupOffset * (popupHeight ?: Int.MAX_VALUE)).dp
                                        IntOffset(0, y.roundToPx())
                                    }
                                    .onSizeChanged { popupHeight = it.height },
                                achievement = currentPopupEvent.achievement,
                            )
                        }
                        is PopupEvent.RAIntegrationPopup -> {
                            RAIntegrationEventUi(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .offset {
                                        val y = (popupOffset * (popupHeight ?: Int.MAX_VALUE)).dp
                                        IntOffset(0, y.roundToPx())
                                    }
                                    .onSizeChanged { popupHeight = it.height },
                                event = currentPopupEvent.event,
                            )
                        }
                        null -> {
                            // Do nothing
                        }
                    }
                }

                if (showAchievementList.value) {
                    val achievementsViewModel = viewModels<EmulatorRetroAchievementsViewModel>().value

                    AchievementListDialog(
                        viewModel = achievementsViewModel,
                        onDismiss = {
                            activeOverlays.removeActiveOverlay(EmulatorOverlay.ACHIEVEMENTS_DIALOG)
                            viewModel.resumeEmulator()
                            showAchievementList.value = false
                        }
                    )
                }

                if (showQuickSettings.value) {
                    QuickSettingsDialog(
                        currentScreen = viewModel.getExternalDisplayScreen(),
                        onScreenSelected = {
                            viewModel.setExternalDisplayScreen(it)
                            setupExternalScreen(true)
                        },
                        onOpenInternalLayout = {
                            startActivity(Intent(this@EmulatorActivity, LayoutListActivity::class.java))
                        },
                        onOpenExternalLayout = {
                            startActivity(Intent(this@EmulatorActivity, ExternalLayoutListActivity::class.java))
                        },
                        onRefreshExternalScreen = {
                            setupExternalScreen(true)
                        },
                        keepAspectRatio = viewModel.isExternalDisplayKeepAspectRatioEnabled(),
                        onKeepAspectRatioChanged = { enabled ->
                            viewModel.setExternalDisplayKeepAspectRatioEnabled(enabled)
                            (externalScreenRender as? ExternalScreenRender)?.let { renderer ->
                                presentation?.queueEvent { renderer.setKeepAspectRatio(enabled) }
                                presentation?.requestRender()
                            }
                        },
                        onDismiss = {
                            viewModel.resumeEmulator()
                            showQuickSettings.value = false
                        }
                    )
                }
            }
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                permissionHandler.observePermissionRequests().collect {
                    permissionRequestLauncher.launch(arrayOf(it))
                }
            }
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.frameRenderEvent.collect { event ->
                    externalScreenRender?.prepareNextFrame(event)
                    if (event.isValidFrame) {
                        presentation?.requestRender()
                    }
                }
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.runtimeLayout.collectLatest {
                    setupSoftInput(it)
                }
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.background.collectLatest {
                    dsRenderer.setBackground(it)
                }
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.externalBackground.collectLatest {
                    (externalScreenRender as? ExternalLayoutRender)?.setBackground(it)
                }
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.runtimeRendererConfiguration.collectLatest {
                    dsRenderer.updateRendererConfiguration(it)
                    externalScreenRender?.updateVideoFiltering(
                        it?.videoFiltering ?: VideoFiltering.NONE
                    )
                }
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentFps.collectLatest {
                    if (it == null) {
                        binding.textFps.text = null
                    } else {
                        binding.textFps.text = getString(R.string.info_fps, it)
                    }
                }
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.toastEvent.collectLatest {
                    val (message, duration) = when (it) {
                        ToastEvent.GbaLoadFailed -> R.string.error_load_gba_rom to Toast.LENGTH_SHORT
                        ToastEvent.QuickSaveSuccessful -> R.string.saved to Toast.LENGTH_SHORT
                        ToastEvent.QuickLoadSuccessful -> R.string.loaded to Toast.LENGTH_SHORT
                        ToastEvent.RewindNotEnabled -> R.string.rewind_not_enabled to Toast.LENGTH_SHORT
                        ToastEvent.RewindNotAvailableWhileRAHardcoreModeEnabled -> R.string.rewind_unavailable_ra_hardcore_enabled to Toast.LENGTH_LONG
                        ToastEvent.StateLoadFailed -> R.string.failed_load_state to Toast.LENGTH_SHORT
                        ToastEvent.StateSaveFailed -> R.string.failed_save_state to Toast.LENGTH_SHORT
                        ToastEvent.StateStateDoesNotExist -> R.string.cant_load_empty_slot to Toast.LENGTH_SHORT
                        ToastEvent.CannotUseSaveStatesWhenRAHardcoreIsEnabled -> R.string.save_states_unavailable_ra_hardcore_enabled to Toast.LENGTH_LONG
                        ToastEvent.CannotLoadStateWhenRunningFirmware,
                        ToastEvent.CannotSaveStateWhenRunningFirmware -> R.string.save_states_not_supported to Toast.LENGTH_LONG
                        ToastEvent.CannotSwitchRetroAchievementsMode -> R.string.retro_achievements_relaunch_to_apply_settings to Toast.LENGTH_LONG
                    }

                    Toast.makeText(this@EmulatorActivity, message, duration).show()
                }
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                        viewModel.uiEvent.collectLatest {
                            when (it) {
                                EmulatorUiEvent.CloseEmulator -> {
                                    Choreographer.getInstance().removeFrameCallback(this@EmulatorActivity)
                                    presentation?.apply {
                                        setBackground("black".toColorInt())
                                        show()
                                    }
                                    finish()
                                }
                                is EmulatorUiEvent.OpenScreen.CheatsScreen -> {
                            val intent = Intent(this@EmulatorActivity, CheatsActivity::class.java)
                            intent.putExtra(CheatsActivity.KEY_ROM_INFO, RomInfoParcelable.fromRomInfo(it.romInfo))
                            cheatsLauncher.launch(intent)
                        }
                        EmulatorUiEvent.OpenScreen.SettingsScreen -> {
                            val settingsIntent = Intent(this@EmulatorActivity, SettingsActivity::class.java)
                            settingsLauncher.launch(settingsIntent)
                        }
                        is EmulatorUiEvent.ShowPauseMenu -> showPauseMenu(it.pauseMenu)
                        is EmulatorUiEvent.ShowRewindWindow -> showRewindWindow(it.rewindWindow)
                        is EmulatorUiEvent.ShowRomSaveStates -> {
                            showSaveStateSlotsDialog(it.saveStates) { slot ->
                                if (it.reason == EmulatorUiEvent.ShowRomSaveStates.Reason.SAVING) {
                                    viewModel.saveStateToSlot(slot)
                                } else {
                                    viewModel.loadStateFromSlot(slot)
                                }
                            }
                        }
                        EmulatorUiEvent.ShowAchievementList -> {
                            activeOverlays.addActiveOverlay(EmulatorOverlay.ACHIEVEMENTS_DIALOG)
                            showAchievementList.value = true
                        }
                        EmulatorUiEvent.ShowQuickSettings -> showQuickSettings.value = true
                    }
                }
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.emulatorState.collectLatest {
                    when (it) {
                        is EmulatorState.Uninitialized -> {
                            binding.viewLayoutControls.isInvisible = true
                            binding.textFps.isGone = true
                            binding.textLoading.isGone = true
                        }
                        EmulatorState.LoadingFirmware,
                        EmulatorState.LoadingRom -> {
                            binding.viewLayoutControls.isInvisible = true
                            binding.textFps.isGone = true
                            binding.textLoading.isVisible = true
                        }
                        is EmulatorState.RunningRom,
                        is EmulatorState.RunningFirmware -> {
                            setupSustainedPerformanceMode()
                            setupFpsCounter()
                            binding.textLoading.isGone = true
                            binding.viewLayoutControls.isVisible = true
                            emulatorReady = true
                            backPressedCallback.isEnabled = true
                        }
                        is EmulatorState.RomLoadError -> {
                            binding.viewLayoutControls.isInvisible = true
                            binding.textFps.isGone = true
                            binding.textLoading.isGone = true
                            showRomLoadErrorDialog()
                        }
                        is EmulatorState.FirmwareLoadError -> {
                            binding.viewLayoutControls.isInvisible = true
                            binding.textFps.isGone = true
                            binding.textLoading.isGone = true
                            showFirmwareLoadErrorDialog(it)
                        }
                        is EmulatorState.RomNotFoundError -> {
                            binding.viewLayoutControls.isInvisible = true
                            binding.textFps.isGone = true
                            binding.textLoading.isGone = true
                            showRomNotFoundDialog(it.romPath)
                        }
                    }
                }
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@EmulatorActivity).windowLayoutInfo(this@EmulatorActivity).collect {
                    val folds = it.displayFeatures.mapNotNull {
                        if (it is FoldingFeature) {
                            ScreenFold(
                                orientation = if (it.orientation == FoldingFeature.Orientation.HORIZONTAL) Orientation.LANDSCAPE else Orientation.PORTRAIT,
                                type = if (it.isSeparating) ScreenFold.FoldType.SEAMLESS else ScreenFold.FoldType.GAP,
                                foldBounds = Rect(it.bounds.left, it.bounds.top, it.bounds.width(), it.bounds.height())
                            )
                        } else {
                            null
                        }
                    }
                    viewModel.setScreenFolds(folds)
                }
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                appForegroundStateObserver.onAppMovedToBackgroundEvent.collect {
                    presentation?.dismiss()
                    presentation = null
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        showExternalDisplay()
        setupExternalScreen(true)
    }


    /**
     * Creates a presentation for an external display if one is connected.
     *
     * This method checks for an available display different from the default
     * device display. When found, it instantiates [ExternalPresentation],
     * stores it in [ExternalDisplayManager] and shows it. If the OpenGL context
     * is already available, it will also share it with the new presentation.
     */
    private fun showExternalDisplay() {
        if (presentation != null) return

        val displays = displayManager.displays
        Log.d("DualScreenEmulator", "Found ${displays.size} displays.")
        for (display in displays) {
            Log.d("DualScreenEmulator", "Display ID: ${display.displayId}, Name: ${display.name}")
        }

        val currentDisplay = ContextCompat.getDisplayOrDefault(this)
        val targetDisplay = if (currentDisplay.displayId != Display.DEFAULT_DISPLAY) {
            displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        } else {
            displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
                .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && it.name != "Built-in Screen" }
                ?:
                displayManager.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        }
        if (targetDisplay != null) {
            Log.d(
                "DualScreenEmulator",
                "Using external display: ID=${targetDisplay.displayId}, Name=${targetDisplay.name}"
            )

            presentation = ExternalPresentation(
                this,
                targetDisplay,
                settingsRepository.isExternalDisplayRotateLeftEnabled(),
            ).apply {

                setSharedContext(dsRenderer.getSharedEglContext())

                setOnShowListener {
                    Log.d("DualScreenEmulator", "Presentation successfully shown on external display.")
                }

                try {
                    show()
                    Log.d("DualScreenEmulator", "Presentation.show() called")
                } catch (e: Exception) {
                    Log.e("DualScreenEmulator", "Error showing presentation: ${e.message}", e)
                }

            }
        }else {
            Log.w("DualScreenEmulator", "No external display found.")
        }
    }

    /**
     * Sets up the external screen if one is connected.
     *
     * This function retrieves the shared EGL context from the DSRenderer and passes it to the
     * external display presentation. It then determines which screen (top, bottom, or custom)
     * should be displayed on the external screen based on the user's settings.
     *
     * If the selected screen configuration has changed or if a custom layout is selected,
     * the function updates the `currentExternalDisplayScreen` and configures the external
     * display presentation accordingly.
     *
     * For custom layouts, it fetches the layout details (top/bottom screen rectangles and UI size)
     * from the `layoutsRepository` and `settingsRepository` to correctly display the custom layout
     * on the external screen.
     */
    private fun setupExternalScreen(
        force: Boolean = false,
        retryDelay: Long = 1000L,
        maxRetries: Int = 120
    ) {
        if (presentation == null) {
            showExternalDisplay()
        }

        val sharedContext = dsRenderer.getSharedEglContext()
        if (sharedContext == null) {
            if (maxRetries > 0) {
                lifecycleScope.launch {
                    delay(retryDelay)
                    setupExternalScreen(force, retryDelay, maxRetries - 1)
                }
            }
            return
        }

        presentation?.let { pres ->
            pres.setSharedContext(sharedContext)
            val screen = viewModel.getExternalDisplayScreen()
            if (force || screen != currentExternalDisplayScreen || screen == DsExternalScreen.CUSTOM) {
                currentExternalDisplayScreen = screen
                externalScreenRender = when (screen) {
                    DsExternalScreen.TOP -> pres.showTopScreen()
                    DsExternalScreen.BOTTOM -> pres.showBottomScreen(melonTouchHandler)
                    DsExternalScreen.CUSTOM -> {
                        val layoutId = viewModel.getExternalLayoutId()
                        val layout = runBlocking { layoutsRepository.getLayout(layoutId) }
                        val entry = layout?.layoutVariants?.entries?.firstOrNull()

                        if (entry != null) {
                            val uiLayout = entry.value
                            val layoutVariant = entry.key
                            val topComponent = uiLayout.components?.firstOrNull { it.component == LayoutComponent.TOP_SCREEN }
                            val bottomComponent = uiLayout.components?.firstOrNull { it.component == LayoutComponent.BOTTOM_SCREEN }
                            val topRect = topComponent?.rect
                            val bottomRect = bottomComponent?.rect
                            val topAlpha = topComponent?.alpha ?: 1f
                            val bottomAlpha = bottomComponent?.alpha ?: 1f
                            val topOnTop = topComponent?.onTop ?: false
                            val bottomOnTop = bottomComponent?.onTop ?: false
                            val uiSize = layoutVariant.uiSize
                            pres.showCustomLayout(
                                topRect,
                                bottomRect,
                                topAlpha,
                                bottomAlpha,
                                topOnTop,
                                bottomOnTop,
                                uiSize.x,
                                uiSize.y,
                                viewModel.externalBackground.value
                            )
                        } else {
                            val metrics = DisplayMetrics()
                            pres.display?.getRealMetrics(metrics)
                            val width = metrics.widthPixels
                            val height = metrics.heightPixels
                            val orientation = if (width > height) Orientation.LANDSCAPE else Orientation.PORTRAIT
                            val defaultLayout = defaultLayoutProvider.buildDefaultLayout(width, height, orientation, emptyList())
                            val topComponent = defaultLayout.components?.firstOrNull { it.component == LayoutComponent.TOP_SCREEN }
                            val bottomComponent = defaultLayout.components?.firstOrNull { it.component == LayoutComponent.BOTTOM_SCREEN }
                            val topRect = topComponent?.rect
                            val bottomRect = bottomComponent?.rect
                            val topAlpha = topComponent?.alpha ?: 1f
                            val bottomAlpha = bottomComponent?.alpha ?: 1f
                            val topOnTop = topComponent?.onTop ?: false
                            val bottomOnTop = bottomComponent?.onTop ?: false
                            pres.showCustomLayout(
                                topRect,
                                bottomRect,
                                topAlpha,
                                bottomAlpha,
                                topOnTop,
                                bottomOnTop,
                                width,
                                height,
                                viewModel.externalBackground.value
                            )
                        }
                    }
                }
                (externalScreenRender as? ExternalScreenRender)?.let { renderer ->
                    val keep = viewModel.isExternalDisplayKeepAspectRatioEnabled()
                    pres.queueEvent { renderer.setKeepAspectRatio(keep) }
                    pres.requestRender()
                }
                externalScreenRender?.updateVideoFiltering(
                    viewModel.runtimeRendererConfiguration.value?.videoFiltering
                        ?: VideoFiltering.NONE
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (viewModel.emulatorState.value.isRunning()) {
            viewModel.pauseEmulator(false)
            backPressedCallback.isEnabled = false

            activeOverlays.addActiveOverlay(EmulatorOverlay.SWITCH_NEW_ROM_DIALOG)
            AlertDialog.Builder(this)
                    .setTitle(getString(R.string.title_emulator_running))
                    .setMessage(getString(R.string.message_stop_emulation))
                    .setPositiveButton(R.string.ok) { _, _ ->
                        viewModel.stopEmulator()
                        setIntent(intent)
                        launchEmulator()
                    }
                    .setNegativeButton(R.string.no) { dialog, _ ->
                        dialog.cancel()
                    }
                    .setOnDismissListener {
                        activeOverlays.removeActiveOverlay(EmulatorOverlay.SWITCH_NEW_ROM_DIALOG)
                    }
                    .setOnCancelListener {
                        backPressedCallback.isEnabled = true
                        viewModel.resumeEmulator()
                    }
                    .show()
        }
    }

    override fun onResume() {
        super.onResume()
        Choreographer.getInstance().postFrameCallback(this)

        setupExternalScreen(true)

        if (!activeOverlays.hasActiveOverlays()) {
            disableScreenTimeOut()
            viewModel.resumeEmulator()
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        binding.surfaceMain.doFrame()
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun launchEmulator() {
        val extras = intent?.extras
        val bootFirmwareOnly = extras?.getBoolean(KEY_BOOT_FIRMWARE_ONLY) ?: false

        lifecycleScope.launch {
            val glContext = currentOpenGlContext.filterNotNull().first()

            disableScreenTimeOut()
            if (bootFirmwareOnly) {
                val consoleTypeParameter = extras.getInt(KEY_BOOT_FIRMWARE_CONSOLE, -1)
                if (consoleTypeParameter == -1) {
                    throw RuntimeException("No console type specified")
                }

                val firmwareConsoleType = ConsoleType.entries[consoleTypeParameter]
                viewModel.loadFirmware(firmwareConsoleType, glContext)
            } else {
                val romParcelable = extras?.parcelable<RomParcelable>(KEY_ROM)

                if (romParcelable?.rom != null) {
                    viewModel.loadRom(romParcelable.rom, glContext)
                } else {
                    if (extras?.containsKey(KEY_PATH) == true) {
                        val romPath = extras.getString(KEY_PATH)!!
                        viewModel.loadRom(romPath, glContext)
                    } else if (extras?.containsKey(KEY_URI) == true) {
                        val romUri = extras.getString(KEY_URI)!!
                        viewModel.loadRom(romUri.toUri(), glContext)
                    } else {
                        throw RuntimeException("No ROM was specified")
                    }
                }
            }

            // Ensure the external display is correctly configured when launching
            // the emulator from external intents such as LAUNCH_ROM. When the
            // activity is started directly from a front-end, the external
            // presentation might not yet be set up. Calling both
            // `showExternalDisplay` and `setupExternalScreen` here guarantees that
            // the presentation is created (if needed) and configured with the
            // proper screen before emulation begins.
            showExternalDisplay()
            setupExternalScreen(true)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        setupFullscreen()
    }

    private fun setupFullscreen() {
        window.insetsControllerCompat?.let {
            it.hide(WindowInsetsCompat.Type.navigationBars())
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun setupSustainedPerformanceMode() {
        window.setSustainedPerformanceMode(viewModel.isSustainedPerformanceModeEnabled())
    }

    private fun setupFpsCounter() {
        val fpsCounterPosition = viewModel.getFpsCounterPosition()
        if (fpsCounterPosition == FpsCounterPosition.HIDDEN) {
            binding.textFps.isGone = true
        } else {
            binding.textFps.isVisible = true
            val newParams = ConstraintLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT)
            when (fpsCounterPosition) {
                FpsCounterPosition.TOP_LEFT -> {
                    newParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                    newParams.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID
                }
                FpsCounterPosition.TOP_CENTER -> {
                    newParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                    newParams.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID
                    newParams.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID
                }
                FpsCounterPosition.TOP_RIGHT -> {
                    newParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                    newParams.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID
                }
                FpsCounterPosition.BOTTOM_LEFT -> {
                    newParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                    newParams.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID
                }
                FpsCounterPosition.BOTTOM_CENTER -> {
                    newParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                    newParams.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID
                    newParams.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID
                }
                FpsCounterPosition.BOTTOM_RIGHT -> {
                    newParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                    newParams.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID
                }
                FpsCounterPosition.HIDDEN -> { /* Do nothing here */ }
            }
            binding.textFps.layoutParams = newParams
        }
    }

    private fun setupSoftInput(layoutConfiguration: RuntimeInputLayoutConfiguration?) {
        if (layoutConfiguration != null) {
            setLayoutOrientation(layoutConfiguration.layoutOrientation)
            with(binding.viewLayoutControls) {
                instantiateLayout(layoutConfiguration)
                setLayoutComponentToggleState(LayoutComponent.BUTTON_FAST_FORWARD_TOGGLE, frontendInputHandler.fastForwardEnabled)
                setLayoutComponentToggleState(LayoutComponent.BUTTON_MICROPHONE_TOGGLE, frontendInputHandler.microphoneEnabled)
            }
        } else {
            binding.viewLayoutControls.destroyLayout()
        }
    }

    private fun swapScreen() {
        binding.viewLayoutControls.swapScreens()
        updateRendererScreenAreas()
        updateExternalScreen()
    }

    private fun updateRendererScreenAreas() {
        val (topScreen, bottomScreen) = if (binding.viewLayoutControls.areScreensSwapped()) {
            LayoutComponent.BOTTOM_SCREEN to LayoutComponent.TOP_SCREEN
        } else {
            LayoutComponent.TOP_SCREEN to LayoutComponent.BOTTOM_SCREEN
        }
        val topView = binding.viewLayoutControls.getLayoutComponentView(topScreen)
        val bottomView = binding.viewLayoutControls.getLayoutComponentView(bottomScreen)
        dsRenderer.updateScreenAreas(
            topView?.getRect(),
            bottomView?.getRect(),
            topView?.baseAlpha ?: 1f,
            bottomView?.baseAlpha ?: 1f,
            topView?.onTop ?: false,
            bottomView?.onTop ?: false,
        )
    }

    private fun updateExternalScreen() {
        val presentation = presentation ?: return

        val swapped = binding.viewLayoutControls.areScreensSwapped()
        val screen = viewModel.getExternalDisplayScreen()

        presentation.setSharedContext(dsRenderer.getSharedEglContext())

        externalScreenRender = when (screen) {
            DsExternalScreen.TOP -> if (swapped) {
                presentation.showBottomScreen(melonTouchHandler)
            } else {
                presentation.showTopScreen()
            }

            DsExternalScreen.BOTTOM -> if (swapped) {
                presentation.showTopScreen()
            } else {
                presentation.showBottomScreen(melonTouchHandler)
            }

            DsExternalScreen.CUSTOM -> {
                val layoutId = viewModel.getExternalLayoutId()
                val layout = runBlocking { layoutsRepository.getLayout(layoutId) }
                val entry = layout?.layoutVariants?.entries?.firstOrNull()
                val uiLayout = entry?.value
                val layoutVariant = entry?.key
                val topComponent = uiLayout?.components?.firstOrNull { it.component == LayoutComponent.TOP_SCREEN }
                val bottomComponent = uiLayout?.components?.firstOrNull { it.component == LayoutComponent.BOTTOM_SCREEN }

                val topRect = if (swapped) bottomComponent?.rect else topComponent?.rect
                val bottomRect = if (swapped) topComponent?.rect else bottomComponent?.rect
                val topAlpha = if (swapped) bottomComponent?.alpha ?: 1f else topComponent?.alpha ?: 1f
                val bottomAlpha = if (swapped) topComponent?.alpha ?: 1f else bottomComponent?.alpha ?: 1f
                val topOnTop = if (swapped) bottomComponent?.onTop ?: false else topComponent?.onTop ?: false
                val bottomOnTop = if (swapped) topComponent?.onTop ?: false else bottomComponent?.onTop ?: false
                val uiSize = layoutVariant?.uiSize

                presentation.showCustomLayout(
                    topRect,
                    bottomRect,
                    topAlpha,
                    bottomAlpha,
                    topOnTop,
                    bottomOnTop,
                    uiSize?.x ?: 0,
                    uiSize?.y ?: 0,
                    viewModel.externalBackground.value,
                )
            }
        }

        (externalScreenRender as? ExternalScreenRender)?.let { renderer ->
            val keep = viewModel.isExternalDisplayKeepAspectRatioEnabled()
            presentation.queueEvent { renderer.setKeepAspectRatio(keep) }
            presentation.requestRender()
        }
        externalScreenRender?.updateVideoFiltering(
            viewModel.runtimeRendererConfiguration.value?.videoFiltering
                ?: VideoFiltering.NONE
        )
    }

    private fun setupInputHandling() {
        nativeInputListener = InputProcessor(settingsRepository.getControllerConfiguration(), melonTouchHandler, frontendInputHandler)
    }

    private fun handleBackPressed() {
        if (isRewindWindowOpen()) {
            closeRewindWindow()
        } else {
            viewModel.pauseEmulator(true)
        }
    }

    private fun showPauseMenu(pauseMenu: PauseMenu) {
        val options = Array(pauseMenu.options.size) {
            getString(pauseMenu.options[it].textResource)
        }

        activeOverlays.addActiveOverlay(EmulatorOverlay.PAUSE_MENU)
        AlertDialog.Builder(this)
                .setTitle(R.string.pause)
                .setItems(options) { _, which ->
                    val selectedOption = pauseMenu.options[which]
                    viewModel.onPauseMenuOptionSelected(selectedOption)
                }
                .setOnDismissListener {
                    activeOverlays.removeActiveOverlay(EmulatorOverlay.PAUSE_MENU)
                }
                .setOnCancelListener {
                    viewModel.resumeEmulator()
                }
                .show()
    }

    private fun disableScreenTimeOut() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun enableScreenTimeOut() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!activeOverlays.hasActiveOverlays() && nativeInputListener.onKeyEvent(event))
            return true

        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (!activeOverlays.hasActiveOverlays() && nativeInputListener.onMotionEvent(event))
            return true

        return super.dispatchGenericMotionEvent(event)
    }

    private fun isRewindWindowOpen(): Boolean {
        return binding.root.currentState == R.id.rewind_visible
    }

    private fun showSaveStateSlotsDialog(slots: List<SaveStateSlot>, onSlotPicked: (SaveStateSlot) -> Unit) {
        val dateFormatter = SimpleDateFormat("EEE, dd MMM yyyy", ConfigurationCompat.getLocales(resources.configuration)[0])
        val timeFormatter = SimpleDateFormat("kk:mm:ss", ConfigurationCompat.getLocales(resources.configuration)[0])
        var dialog: AlertDialog? = null
        var adapter: SaveStateAdapter? = null

        adapter = SaveStateAdapter(
            slots = slots,
            picasso = picasso,
            dateFormat = dateFormatter,
            timeFormat = timeFormatter,
            onSlotSelected = {
                dialog?.dismiss()
                onSlotPicked(it)
            },
            onDeletedSlot = {
                viewModel.deleteSaveStateSlot(it)?.let { newSlots ->
                    adapter?.updateSaveStateSlots(newSlots)
                }
            },
        )

        val recyclerView = RecyclerView(this).apply {
            val layoutManager = LinearLayoutManager(this@EmulatorActivity)
            this.layoutManager = layoutManager
            addItemDecoration(DividerItemDecoration(context, layoutManager.orientation))
            this.adapter = adapter
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }

        activeOverlays.addActiveOverlay(EmulatorOverlay.SAVE_STATES_DIALOG)

        dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.save_slot))
            .setView(recyclerView)
            .setNegativeButton(R.string.cancel) { _dialog, _ ->
                _dialog.cancel()
            }
            .setOnDismissListener {
                activeOverlays.removeActiveOverlay(EmulatorOverlay.SAVE_STATES_DIALOG)
            }
            .setOnCancelListener {
                viewModel.resumeEmulator()
            }
            .show()
    }

    private fun showRomLoadErrorDialog() {
        activeOverlays.addActiveOverlay(EmulatorOverlay.ROM_LOAD_ERROR_DIALOG)
        AlertDialog.Builder(this)
            .setCancelable(false)
            .setTitle(R.string.error_load_rom)
            .setMessage(R.string.error_load_rom_message)
            .setPositiveButton(R.string.ok) { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .show()
    }

    private fun showRomNotFoundDialog(romPath: String) {
        activeOverlays.addActiveOverlay(EmulatorOverlay.ROM_NOT_FOUND_DIALOG)
        AlertDialog.Builder(this)
            .setTitle(R.string.error_rom_not_found)
            .setMessage(getString(R.string.error_rom_not_found_info, romPath))
            .setPositiveButton(R.string.ok) { _, _ ->
                finish()
            }
            .setOnDismissListener {
                finish()
            }
            .show()
    }

    private fun showFirmwareLoadErrorDialog(error: EmulatorState.FirmwareLoadError) {
        activeOverlays.addActiveOverlay(EmulatorOverlay.FIRMWARE_LOAD_ERROR_DIALOG)
        AlertDialog.Builder(this)
            .setCancelable(false)
            .setTitle(R.string.error_load_firmware)
            .setMessage(resources.getString(R.string.error_load_firmware_message, error.reason.toString()))
            .setPositiveButton(R.string.ok) { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .show()
    }

    private fun showRewindWindow(rewindWindow: RewindWindow) {
        activeOverlays.addActiveOverlay(EmulatorOverlay.REWIND_WINDOW)
        binding.root.transitionToState(R.id.rewind_visible)
        rewindSaveStateAdapter.setRewindWindow(rewindWindow)
    }

    private fun closeRewindWindow() {
        activeOverlays.removeActiveOverlay(EmulatorOverlay.REWIND_WINDOW)
        binding.root.transitionToState(R.id.rewind_hidden)
        viewModel.resumeEmulator()
    }

    private fun updateOrientation(configuration: Configuration) {
        val orientation = if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Orientation.PORTRAIT
        } else {
            Orientation.LANDSCAPE
        }
        viewModel.setSystemOrientation(orientation)
    }

    override fun onPause() {
        super.onPause()
        enableScreenTimeOut()
        Choreographer.getInstance().removeFrameCallback(this)
        viewModel.pauseEmulator(false)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateOrientation(newConfig)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.surfaceMain.stop()
        presentation?.dismiss()
        displayManager.unregisterDisplayListener(displayListener)
    }
}
