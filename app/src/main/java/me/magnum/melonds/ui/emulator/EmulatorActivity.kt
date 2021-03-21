package me.magnum.melonds.ui.emulator

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.CompletableObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ActivityEmulatorBinding
import me.magnum.melonds.domain.model.*
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.parcelables.RomParcelable
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
    @Inject lateinit var settingsRepository: SettingsRepository
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
            pauseEmulation()
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
    }

    private var emulatorReady = false
    private var emulatorPaused = false
    private var softInputVisible = true
    private var screensSwapped = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmulatorBinding.inflate(layoutInflater)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setupFullscreen()
        setContentView(binding.root)
        initializeDelegate()

        melonTouchHandler = MelonTouchHandler()
        dsRenderer = DSRenderer(buildRendererConfiguration())
        dsRenderer.setRendererListener(this)
        binding.surfaceMain.apply {
            setEGLContextClientVersion(2)
            setRenderer(dsRenderer)
        }

        binding.textFps.visibility = View.INVISIBLE
        binding.viewLayoutControls.setLayoutComponentViewBuilderFactory(RuntimeLayoutComponentViewBuilderFactory())

        viewModel.getLayout().observe(this, Observer {
            softInputVisible = true
            setupSoftInput(it)
        })

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
        setupObservable.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : CompletableObserver {
                    override fun onSubscribe(disposable: Disposable) {
                        binding.textFps.visibility = View.GONE
                        binding.textLoading.visibility = View.VISIBLE
                    }

                    override fun onComplete() {
                        try {
                            MelonEmulator.startEmulation()
                            binding.textFps.visibility = View.VISIBLE
                            binding.textLoading.visibility = View.GONE
                            emulatorReady = true
                        } catch (e: Exception) {
                            showLaunchFailDialog(e)
                        }
                    }

                    override fun onError(e: Throwable) {
                        e.printStackTrace()
                        showLaunchFailDialog(e)
                    }
                })
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        setupFullscreen()
    }

    private fun buildRendererConfiguration(): RendererConfiguration {
        return RendererConfiguration(settingsRepository.getVideoFiltering(), settingsRepository.isThreadedRenderingEnabled())
    }

    private fun setupFullscreen() {
        this.window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    private fun setupSoftInput(layoutConfiguration: LayoutConfiguration) {
        val layoutChangeListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            dsRenderer.updateScreenAreas(
                    binding.viewLayoutControls.getLayoutComponentView(LayoutComponent.TOP_SCREEN)?.getRect(),
                    binding.viewLayoutControls.getLayoutComponentView(LayoutComponent.BOTTOM_SCREEN)?.getRect()
            )
            updateSoftInput()
        }
        binding.viewLayoutControls.addOnLayoutChangeListener(layoutChangeListener)
        binding.viewLayoutControls.instantiateLayout(layoutConfiguration)
    }

    private fun updateSoftInput() {
        if (settingsRepository.showSoftInput()) {
            val opacity = settingsRepository.getSoftInputOpacity()
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
        this.pauseEmulation()
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
        if (emulatorReady)
            MelonEmulator.stopEmulation()

        super.onDestroy()
    }
}