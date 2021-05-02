package me.magnum.melonds.ui.emulator

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.disposables.Disposable
import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.R
import me.magnum.melonds.common.Schedulers
import me.magnum.melonds.common.UriFileHandler
import me.magnum.melonds.common.uridelegates.UriHandler
import me.magnum.melonds.databinding.ActivityEmulatorBinding
import me.magnum.melonds.domain.model.*
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.parcelables.RomInfoParcelable
import me.magnum.melonds.parcelables.RomParcelable
import me.magnum.melonds.ui.cheats.CheatsActivity
import me.magnum.melonds.ui.emulator.DSRenderer.RendererListener
import me.magnum.melonds.ui.emulator.input.*
import me.magnum.melonds.ui.settings.SettingsActivity
import java.net.URLEncoder
import java.nio.ByteBuffer
import javax.inject.Inject

@AndroidEntryPoint
class EmulatorActivity : AppCompatActivity(), RendererListener {
    companion object {
        const val KEY_ROM = "rom"
        const val KEY_PATH = "PATH"
        const val KEY_URI = "uri"
        const val KEY_BOOT_FIRMWARE_CONSOLE = "boot_firmware_console"
        private const val KEY_BOOT_FIRMWARE_ONLY = "boot_firmware_only"

        init {
            System.loadLibrary("melonDS-android-frontend")
        }

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

    class RomLoadFailedException(reason: String) : Exception("Failed to load ROM: $reason") {
        constructor(result: MelonEmulator.LoadResult) : this(result.toString())
    }
    class FirmwareLoadFailedException(result: MelonEmulator.FirmwareLoadResult) : Exception("Failed to load firmware: $result")

    interface PauseMenuOption {
        val textResource: Int
    }

    private lateinit var binding: ActivityEmulatorBinding
    val viewModel: EmulatorViewModel by viewModels()
    @Inject
    lateinit var settingsRepository: SettingsRepository
    @Inject
    lateinit var uriHandler: UriHandler
    @Inject
    lateinit var picasso: Picasso
    @Inject
    lateinit var schedulers: Schedulers
    private lateinit var delegate: EmulatorDelegate

    private lateinit var dsRenderer: DSRenderer
    private lateinit var melonTouchHandler: MelonTouchHandler
    private lateinit var nativeInputListener: INativeInputListener
    private val frontendInputHandler = object : FrontendInputHandler() {
        private var fastForwardEnabled = false

        override fun onSoftInputTogglePressed() {
            toggleSoftInputVisibility()
        }

        override fun onPausePressed() {
            if (emulatorReady) {
                pauseEmulation()
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
    }
    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val newEmulatorConfiguration = delegate.getEmulatorConfiguration()
        MelonEmulator.updateEmulatorConfiguration(newEmulatorConfiguration)
        dsRenderer.updateRendererConfiguration(newEmulatorConfiguration.rendererConfiguration)
        updateSoftInput()
        setupInputHandling()
        setupSustainedPerformanceMode()
        setupFpsCounter()
    }
    private val cheatsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        cheatsClosedListener?.invoke()
        cheatsClosedListener = null
    }

    private var emulatorSetupDisposable: Disposable? = null
    private var cheatsClosedListener: (() -> Unit)? = null
    private var emulatorReady = false
    private var emulatorPaused = false
    private var softInputVisible = true
    private var screensSwapped = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmulatorBinding.inflate(layoutInflater)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(binding.root)
        setupFullscreen()
        initializeDelegate()

        melonTouchHandler = MelonTouchHandler()
        dsRenderer = DSRenderer(buildRendererConfiguration(), this)
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

        viewModel.getBackground().observe(this) {
            dsRenderer.setBackground(it)
        }
        viewModel.getLayout().observe(this) {
            softInputVisible = true
            setupSoftInput(it)
        }

        setupInputHandling()
        launchEmulator()
    }

    private fun initializeDelegate() {
        val bootFirmwareOnly = intent.extras?.getBoolean(KEY_BOOT_FIRMWARE_ONLY) ?: false
        delegate = if (bootFirmwareOnly) FirmwareEmulatorDelegate(this) else RomEmulatorDelegate(this)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        if (emulatorReady) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            MelonEmulator.pauseEmulation()
            emulatorPaused = true
            emulatorReady = false

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
                        resumeEmulation()
                    }
                    .show()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.surfaceMain.onResume()

        if (emulatorReady && !emulatorPaused)
            MelonEmulator.resumeEmulation()
    }

    private fun launchEmulator() {
        // Force view model resolution
        viewModel.let { }
        val setupObservable = delegate.getEmulatorSetupObservable(intent.extras)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        emulatorSetupDisposable = setupObservable.subscribeOn(schedulers.backgroundThreadScheduler)
                .observeOn(schedulers.uiThreadScheduler)
                .doOnSubscribe {
                    runOnUiThread {
                        binding.viewLayoutControls.isInvisible = true
                        binding.textFps.isGone = true
                        binding.textLoading.isVisible = true
                    }
                }
                .subscribe({
                    try {
                        setupSustainedPerformanceMode()
                        MelonEmulator.startEmulation()
                        setupFpsCounter()
                        binding.textLoading.visibility = View.GONE
                        binding.viewLayoutControls.isVisible = true
                        dsRenderer.canRenderBackground = true
                        emulatorReady = true
                    } catch (e: Exception) {
                        showLaunchFailDialog(e)
                    }
                }) {
                    it.printStackTrace()
                    showLaunchFailDialog(it)
                }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        setupFullscreen()
    }

    private fun buildRendererConfiguration(): RendererConfiguration {
        return RendererConfiguration(settingsRepository.getVideoFiltering(), settingsRepository.isThreadedRenderingEnabled())
    }

    private fun setupFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
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
            val newParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT)
            when (fpsCounterPosition) {
                FpsCounterPosition.TOP_LEFT -> {
                    newParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                    newParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
                }
                FpsCounterPosition.TOP_CENTER -> {
                    newParams.addRule(RelativeLayout.CENTER_HORIZONTAL)
                    newParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
                }
                FpsCounterPosition.TOP_RIGHT -> {
                    newParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                    newParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
                }
                FpsCounterPosition.BOTTOM_LEFT -> {
                    newParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                    newParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                }
                FpsCounterPosition.BOTTOM_CENTER -> {
                    newParams.addRule(RelativeLayout.CENTER_HORIZONTAL)
                    newParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                }
                FpsCounterPosition.BOTTOM_RIGHT -> {
                    newParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                    newParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                }
            }
            binding.textFps.layoutParams = newParams
        }
    }

    private fun setupSoftInput(layoutConfiguration: LayoutConfiguration) {
        val layoutChangeListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            dsRenderer.updateScreenAreas(
                    binding.viewLayoutControls.getLayoutComponentView(LayoutComponent.TOP_SCREEN)?.getRect(),
                    binding.viewLayoutControls.getLayoutComponentView(LayoutComponent.BOTTOM_SCREEN)?.getRect()
            )

            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                viewModel.setOrientation(Orientation.PORTRAIT)
            } else {
                viewModel.setOrientation(Orientation.LANDSCAPE)
            }
            updateSoftInput()
        }
        binding.viewLayoutControls.addOnLayoutChangeListener(layoutChangeListener)
        binding.viewLayoutControls.instantiateLayout(layoutConfiguration)
    }

    private fun updateSoftInput() {
        if (settingsRepository.showSoftInput()) {
            val opacity = viewModel.getSoftInputOpacity()
            val inputAlpha = opacity / 100f

            val enableHapticFeedback = viewModel.isTouchHapticFeedbackEnabled()
            binding.viewLayoutControls.getLayoutComponentView(LayoutComponent.DPAD)?.view?.setOnTouchListener(DpadInputHandler(melonTouchHandler, enableHapticFeedback))
            binding.viewLayoutControls.getLayoutComponentView(LayoutComponent.BUTTONS)?.view?.setOnTouchListener(ButtonsInputHandler(melonTouchHandler, enableHapticFeedback))
            binding.viewLayoutControls.getLayoutComponentView(LayoutComponent.BUTTON_L)?.view?.setOnTouchListener(SingleButtonInputHandler(melonTouchHandler, Input.L, false, enableHapticFeedback))
            binding.viewLayoutControls.getLayoutComponentView(LayoutComponent.BUTTON_R)?.view?.setOnTouchListener(SingleButtonInputHandler(melonTouchHandler, Input.R, false, enableHapticFeedback))
            binding.viewLayoutControls.getLayoutComponentView(LayoutComponent.BUTTON_SELECT)?.view?.setOnTouchListener(SingleButtonInputHandler(melonTouchHandler, Input.SELECT, false, enableHapticFeedback))
            binding.viewLayoutControls.getLayoutComponentView(LayoutComponent.BUTTON_START)?.view?.setOnTouchListener(SingleButtonInputHandler(melonTouchHandler, Input.START, false, enableHapticFeedback))
            binding.viewLayoutControls.getLayoutComponentView(LayoutComponent.BUTTON_HINGE)?.view?.setOnTouchListener(SingleButtonInputHandler(melonTouchHandler, Input.HINGE, true, enableHapticFeedback))
            binding.viewLayoutControls.getLayoutComponentView(LayoutComponent.BUTTON_RESET)?.view?.setOnTouchListener(SingleButtonInputHandler(frontendInputHandler, Input.RESET, true, enableHapticFeedback))
            binding.viewLayoutControls.getLayoutComponentView(LayoutComponent.BUTTON_PAUSE)?.view?.setOnTouchListener(SingleButtonInputHandler(frontendInputHandler, Input.PAUSE, true, enableHapticFeedback))
            binding.viewLayoutControls.getLayoutComponentView(LayoutComponent.BUTTON_FAST_FORWARD_TOGGLE)?.view?.setOnTouchListener(SingleButtonInputHandler(frontendInputHandler, Input.FAST_FORWARD, false, enableHapticFeedback))
            binding.viewLayoutControls.getLayoutComponentView(LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT)?.view?.setOnTouchListener(SingleButtonInputHandler(frontendInputHandler, Input.TOGGLE_SOFT_INPUT, false, enableHapticFeedback))
            binding.viewLayoutControls.getLayoutComponentView(LayoutComponent.BUTTON_SWAP_SCREENS)?.view?.setOnTouchListener(SingleButtonInputHandler(frontendInputHandler, Input.SWAP_SCREENS, false, enableHapticFeedback))

            binding.viewLayoutControls.getLayoutComponentViews().forEach {
                if (!it.component.isScreen()) {
                    it.view.apply {
                        visibility = View.VISIBLE
                        alpha = inputAlpha
                    }
                }
            }
            binding.viewLayoutControls.setSoftInputVisibility(softInputVisible)
        } else {
            binding.viewLayoutControls.getLayoutComponentViews().forEach {
                if (!it.component.isScreen()) {
                    it.view.apply {
                        visibility = View.GONE
                    }
                }
            }
        }

        val touchScreenComponent = if (screensSwapped) {
            LayoutComponent.TOP_SCREEN
        } else {
            LayoutComponent.BOTTOM_SCREEN
        }
        binding.viewLayoutControls.getLayoutComponentView(touchScreenComponent)?.view?.setOnTouchListener(TouchscreenInputHandler(melonTouchHandler))
    }

    private fun toggleSoftInputVisibility() {
        softInputVisible = !softInputVisible
        binding.viewLayoutControls.setSoftInputVisibility(softInputVisible)
    }

    private fun swapScreen() {
        screensSwapped = !screensSwapped

        val topScreenRect: Rect?
        val bottomScreenRect: Rect?

        if (screensSwapped) {
            topScreenRect = binding.viewLayoutControls.getLayoutComponentView(LayoutComponent.BOTTOM_SCREEN)?.getRect()
            bottomScreenRect = binding.viewLayoutControls.getLayoutComponentView(LayoutComponent.TOP_SCREEN)?.getRect()
        } else {
            topScreenRect = binding.viewLayoutControls.getLayoutComponentView(LayoutComponent.TOP_SCREEN)?.getRect()
            bottomScreenRect = binding.viewLayoutControls.getLayoutComponentView(LayoutComponent.BOTTOM_SCREEN)?.getRect()
        }

        dsRenderer.updateScreenAreas(
                topScreenRect,
                bottomScreenRect
        )
        updateSoftInput()
    }

    private fun setupInputHandling() {
        nativeInputListener = InputProcessor(settingsRepository.getControllerConfiguration(), melonTouchHandler, frontendInputHandler)
    }

    override fun onBackPressed() {
        if (emulatorReady) {
            this.pauseEmulation()
        } else {
            super.onBackPressed()
        }
    }

    fun pauseEmulation() {
        emulatorPaused = true
        val values = delegate.getPauseMenuOptions()
        val options = Array(values.size) { i -> getString(values[i].textResource) }

        MelonEmulator.pauseEmulation()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        AlertDialog.Builder(this)
                .setTitle(R.string.pause)
                .setItems(options) { _, which -> delegate.onPauseMenuOptionSelected(values[which]) }
                .setOnCancelListener { resumeEmulation()}
                .show()
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

    fun buildUriFileHandler(): UriFileHandler {
        return UriFileHandler(this, uriHandler)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (nativeInputListener.onKeyEvent(event))
            return true

        return super.dispatchKeyEvent(event)
    }

    override fun updateFrameBuffer(dst: ByteBuffer) {
        if (!emulatorReady)
            return

        MelonEmulator.copyFrameBuffer(dst)
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

    private fun showLaunchFailDialog(e: Throwable) {
        AlertDialog.Builder(this)
                .setTitle(R.string.error_load_rom)
                .setMessage(R.string.error_load_rom_report_issue)
                .setPositiveButton(R.string.ok) { dialog, _ ->
                    val intent = getGitHubReportIntent(e)
                    startActivity(intent)
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                .setCancelable(true)
                .setOnDismissListener {
                    finish()
                }
                .show()
    }

    private fun getGitHubReportIntent(e: Throwable): Intent {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)

        val errorBody = "" +
                "* **Device model:** ${Build.MODEL}\n" +
                "* **Android version:** Android API ${Build.VERSION.SDK_INT}\n" +
                "* **melonDS version:** ${packageInfo.versionName}\n\n" +
                "**Problem:**  \n" +
                "Insert a small description of the problem.\n\n" +
                "**Stack trace:**  \n" +
                "```\n" +
                e.stackTraceToString() +
                "\n```\n\n" +
                "**Configuration:**  \n" +
                "```\n" +
                delegate.getCrashContext() +
                "\n```\n"

        val urlEncodedBody = URLEncoder.encode(errorBody, "utf-8")
        val url = "https://github.com/rafaelvcaetano/melonDS-android/issues/new?labels=app%20report&body=$urlEncodedBody"

        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(url)
        }
    }

    override fun onPause() {
        super.onPause()
        binding.surfaceMain.onPause()

        if (emulatorReady && !emulatorPaused)
            MelonEmulator.pauseEmulation()
    }

    override fun onDestroy() {
        if (emulatorReady) {
            MelonEmulator.stopEmulation()
        }

        emulatorSetupDisposable?.dispose()
        delegate.dispose()
        super.onDestroy()
    }
}