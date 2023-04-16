package me.magnum.melonds.ui.emulator

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.R
import me.magnum.melonds.common.PermissionHandler
import me.magnum.melonds.common.runtime.FrameBufferProvider
import me.magnum.melonds.databinding.ActivityEmulatorBinding
import me.magnum.melonds.domain.model.*
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.extensions.insetsControllerCompat
import me.magnum.melonds.extensions.setLayoutOrientation
import me.magnum.melonds.parcelables.RomInfoParcelable
import me.magnum.melonds.parcelables.RomParcelable
import me.magnum.melonds.ui.cheats.CheatsActivity
import me.magnum.melonds.ui.emulator.DSRenderer.RendererListener
import me.magnum.melonds.ui.emulator.firmware.FirmwareEmulatorDelegate
import me.magnum.melonds.ui.emulator.input.FrontendInputHandler
import me.magnum.melonds.ui.emulator.input.INativeInputListener
import me.magnum.melonds.ui.emulator.input.InputProcessor
import me.magnum.melonds.ui.emulator.input.MelonTouchHandler
import me.magnum.melonds.ui.emulator.model.EmulatorState
import me.magnum.melonds.ui.emulator.model.RuntimeInputLayoutConfiguration
import me.magnum.melonds.ui.emulator.rewind.EdgeSpacingDecorator
import me.magnum.melonds.ui.emulator.rewind.RewindSaveStateAdapter
import me.magnum.melonds.ui.emulator.rom.RomEmulatorDelegate
import me.magnum.melonds.ui.emulator.ui.AchievementPopupUi
import me.magnum.melonds.ui.settings.SettingsActivity
import me.magnum.rcheevosapi.model.RAAchievement
import javax.inject.Inject

@AndroidEntryPoint
class EmulatorActivity : AppCompatActivity(), RendererListener {
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
    lateinit var frameBufferProvider: FrameBufferProvider

    @Inject
    lateinit var permissionHandler: PermissionHandler

    private lateinit var delegate: EmulatorDelegate

    private lateinit var dsRenderer: DSRenderer
    private lateinit var melonTouchHandler: MelonTouchHandler
    private lateinit var nativeInputListener: INativeInputListener
    private val frontendInputHandler = object : FrontendInputHandler() {
        private var fastForwardEnabled = false

        override fun onSoftInputTogglePressed() {
            binding.viewLayoutControls.toggleSoftInputVisibility()
        }

        override fun onPausePressed() {
            if (emulatorReady) {
                showPauseMenu()
            }
        }

        override fun onFastForwardPressed() {
            fastForwardEnabled = !fastForwardEnabled
            MelonEmulator.setFastForwardEnabled(fastForwardEnabled)
        }

        override fun onResetPressed() {
            if (emulatorReady) {
                resetEmulation()
            }
        }

        override fun onSwapScreens() {
            swapScreen()
        }

        override fun onQuickSave() {
            performQuickSave()
        }

        override fun onQuickLoad() {
            performQuickLoad()
        }

        override fun onRewind() {
            if (viewModel.isRewindEnabled()) {
                pauseEmulation()
                openRewindWindow()
            } else {
                Toast.makeText(this@EmulatorActivity, R.string.rewind_not_enabled, Toast.LENGTH_SHORT).show()
            }
        }
    }
    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onSettingsChanged()
        setupInputHandling()
        setupSustainedPerformanceMode()
        setupFpsCounter()
    }
    private val cheatsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        cheatsClosedListener?.invoke()
        cheatsClosedListener = null
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
        MelonEmulator.loadRewindState(it)
        closeRewindWindow()
    }
    private var cheatsClosedListener: (() -> Unit)? = null
    private var emulatorReady = false
    private var emulatorPaused = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmulatorBinding.inflate(layoutInflater)
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(binding.root)
        setupFullscreen()
        initializeDelegate()

        onBackPressedDispatcher.addCallback(backPressedCallback)

        melonTouchHandler = MelonTouchHandler()
        dsRenderer = DSRenderer(frameBufferProvider.frameBuffer(), this)
        dsRenderer.setRendererListener(this)
        binding.surfaceMain.apply {
            setEGLContextClientVersion(2)
            preserveEGLContextOnPause = true
            /*setEGLConfigChooser(8, 8, 8, 8, 0, 0)
            holder.setFormat(PixelFormat.RGBA_8888)*/
            setRenderer(dsRenderer)
        }

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

        val layoutChangeListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateRendererScreenAreas()

            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                viewModel.setSystemOrientation(Orientation.PORTRAIT)
            } else {
                viewModel.setSystemOrientation(Orientation.LANDSCAPE)
            }
        }
        binding.root.addOnLayoutChangeListener(layoutChangeListener)

        setupInputHandling()
        launchEmulator()

        binding.layoutAchievement.setContent {
            var currentAchievement by remember {
                mutableStateOf<RAAchievement?>(null)
            }
            var popupOffset by remember {
                mutableStateOf(-1f)
            }
            var popupHeight by remember {
                mutableStateOf<Int?>(null)
            }

            LaunchedEffect(null) {
                viewModel.achievementTriggeredEvent.collect {
                    currentAchievement = it
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
                    currentAchievement = null
                }
            }

            Box(Modifier.fillMaxWidth()) {
                currentAchievement?.let { achievement ->
                    AchievementPopupUi(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (popupOffset * (popupHeight ?: Int.MAX_VALUE)).dp)
                            .onSizeChanged { popupHeight = it.height },
                        achievement = achievement,
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
                viewModel.runtimeRendererConfiguration.collectLatest {
                    dsRenderer.updateRendererConfiguration(it)
                }
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.gbaLoadFailedEvent.collectLatest {
                    Toast.makeText(this@EmulatorActivity, R.string.error_load_gba_rom, Toast.LENGTH_SHORT).show()
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
                            dsRenderer.canRenderBackground = true
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
                        EmulatorState.RomNotFoundError -> {
                            binding.viewLayoutControls.isInvisible = true
                            binding.textFps.isGone = true
                            binding.textLoading.isGone = true
                            showRomNotFoundDialog()
                        }
                    }
                }
            }
        }
    }

    private fun initializeDelegate() {
        val bootFirmwareOnly = intent.extras?.getBoolean(KEY_BOOT_FIRMWARE_ONLY) ?: false
        delegate = if (bootFirmwareOnly) FirmwareEmulatorDelegate(this) else RomEmulatorDelegate(this, picasso)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        if (emulatorReady) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            MelonEmulator.pauseEmulation()
            emulatorPaused = true
            emulatorReady = false
            backPressedCallback.isEnabled = false

            AlertDialog.Builder(this)
                    .setTitle(getString(R.string.title_emulator_running))
                    .setMessage(getString(R.string.message_stop_emulation))
                    .setPositiveButton(R.string.ok) { _, _ ->
                        MelonEmulator.stopEmulation()
                        emulatorPaused = false
                        setIntent(intent)
                        delegate.dispose()
                        initializeDelegate()
                        launchEmulator()
                    }
                    .setNegativeButton(R.string.no) { dialog, _ ->
                        dialog.cancel()
                    }
                    .setOnCancelListener {
                        emulatorReady = true
                        backPressedCallback.isEnabled = true
                        resumeEmulation()
                    }
                    .show()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.surfaceMain.onResume()

        if (emulatorReady && !emulatorPaused) {
            MelonEmulator.resumeEmulation()
        }
    }

    private fun launchEmulator() {
        delegate.getEmulatorSetupObservable(intent.extras)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            window.setSustainedPerformanceMode(viewModel.isSustainedPerformanceModeEnabled())
        }
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
            binding.viewLayoutControls.instantiateLayout(layoutConfiguration)
        }
    }

    private fun swapScreen() {
        binding.viewLayoutControls.swapScreens()
        updateRendererScreenAreas()
    }

    private fun performQuickSave() {
        delegate.performQuickSave()
    }

    private fun performQuickLoad() {
        delegate.performQuickLoad()
    }

    private fun updateRendererScreenAreas() {
        val (topScreen, bottomScreen) = if (binding.viewLayoutControls.areScreensSwapped()) {
            LayoutComponent.BOTTOM_SCREEN to LayoutComponent.TOP_SCREEN
        } else {
            LayoutComponent.TOP_SCREEN to LayoutComponent.BOTTOM_SCREEN
        }
        dsRenderer.updateScreenAreas(
                binding.viewLayoutControls.getLayoutComponentView(topScreen)?.getRect(),
                binding.viewLayoutControls.getLayoutComponentView(bottomScreen)?.getRect()
        )
    }

    private fun setupInputHandling() {
        nativeInputListener = InputProcessor(settingsRepository.getControllerConfiguration(), melonTouchHandler, frontendInputHandler)
    }

    private fun handleBackPressed() {
        if (isRewindWindowOpen()) {
            closeRewindWindow()
        } else {
            this.showPauseMenu()
        }
    }

    private fun showPauseMenu() {
        pauseEmulation()
        val values = delegate.getPauseMenuOptions()
        val options = Array(values.size) { i -> getString(values[i].textResource) }

        AlertDialog.Builder(this)
                .setTitle(R.string.pause)
                .setItems(options) { _, which -> delegate.onPauseMenuOptionSelected(values[which]) }
                .setOnCancelListener { resumeEmulation() }
                .show()
    }

    private fun pauseEmulation() {
        emulatorPaused = true
        MelonEmulator.pauseEmulation()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun resumeEmulation() {
        emulatorPaused = false
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        MelonEmulator.resumeEmulation()
    }

    fun resetEmulation() {
        val result = MelonEmulator.resetEmulation()
        if (!result) {
            Toast.makeText(this, R.string.failed_reset_emulation, Toast.LENGTH_SHORT).show()
        }
    }

    fun takeScreenshot(): Bitmap {
        return dsRenderer.takeScreenshot()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!isRewindWindowOpen() && nativeInputListener.onKeyEvent(event))
            return true

        return super.dispatchKeyEvent(event)
    }

    override fun onFrameRendered() {
        if (!emulatorReady)
            return

        val fps = MelonEmulator.getFPS()
        runOnUiThread { binding.textFps.text = getString(R.string.info_fps, fps) }
    }

    fun openSettings() {
        // Allow emulator to resume once the user returns from Settings
        emulatorPaused = false
        val settingsIntent = Intent(this, SettingsActivity::class.java)
        settingsLauncher.launch(Intent(settingsIntent))
    }

    fun openCheats(rom: Rom, onCheatsClosed: () -> Unit) {
        cheatsClosedListener = onCheatsClosed
        val romInfo = viewModel.getRomInfo(rom) ?: return

        val intent = Intent(this, CheatsActivity::class.java)
        intent.putExtra(CheatsActivity.KEY_ROM_INFO, RomInfoParcelable.fromRomInfo(romInfo))
        cheatsLauncher.launch(intent)
    }

    private fun isRewindWindowOpen(): Boolean {
        return binding.root.currentState == R.id.rewind_visible
    }

    private fun showRomLoadErrorDialog() {
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

    private fun showRomNotFoundDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.error_rom_not_found)
            .setMessage(R.string.error_rom_not_found_info)
            .setPositiveButton(R.string.ok) { _, _ ->
                finish()
            }
            .setOnDismissListener {
                finish()
            }
            .show()
    }

    private fun showFirmwareLoadErrorDialog(error: EmulatorState.FirmwareLoadError) {
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

    fun openRewindWindow() {
        binding.root.transitionToState(R.id.rewind_visible)
        val rewindWindow = MelonEmulator.getRewindWindow()
        rewindSaveStateAdapter.setRewindWindow(rewindWindow)
    }

    private fun closeRewindWindow() {
        binding.root.transitionToState(R.id.rewind_hidden)
        MelonEmulator.resumeEmulation()
    }

    override fun onPause() {
        super.onPause()
        binding.surfaceMain.onPause()

        if (emulatorReady && !emulatorPaused) {
            MelonEmulator.pauseEmulation()
        }
    }

    override fun onDestroy() {
        if (emulatorReady) {
            MelonEmulator.stopEmulation()
        }

        delegate.dispose()
        super.onDestroy()
    }
}