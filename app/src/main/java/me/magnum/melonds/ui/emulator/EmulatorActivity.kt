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
import android.widget.RelativeLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.CompletableObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ActivityEmulatorBinding
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.RendererConfiguration
import me.magnum.melonds.domain.model.Rom
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

        override fun onPausePressed() {
            pauseEmulation()
        }

        override fun onFastForwardPressed() {
            fastForwardEnabled = !fastForwardEnabled
            MelonEmulator.setFastForwardEnabled(fastForwardEnabled)
        }
    }
    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val newEmulatorConfiguration = delegate.getEmulatorConfiguration()
        MelonEmulator.updateEmulatorConfiguration(newEmulatorConfiguration)
        dsRenderer.updateRendererConfiguration(newEmulatorConfiguration.rendererConfiguration)
        setupSoftInput()
        setupInputHandling()
    }

    private var emulatorReady = false
    private var emulatorPaused = false

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

        binding.viewInputArea.setOnTouchListener(TouchscreenInputHandler(melonTouchHandler))
        binding.imageDpad.setOnTouchListener(DpadInputHandler(melonTouchHandler))
        binding.imageButtons.setOnTouchListener(ButtonsInputHandler(melonTouchHandler))
        binding.imageButtonL.setOnTouchListener(SingleButtonInputHandler(melonTouchHandler, Input.L))
        binding.imageButtonR.setOnTouchListener(SingleButtonInputHandler(melonTouchHandler, Input.R))
        binding.imageButtonSelect.setOnTouchListener(SingleButtonInputHandler(melonTouchHandler, Input.SELECT))
        binding.imageButtonStart.setOnTouchListener(SingleButtonInputHandler(melonTouchHandler, Input.START))
        binding.imageButtonLid.setOnTouchListener(SingleButtonInputHandler(melonTouchHandler, Input.HINGE, true))
        binding.imageButtonFastForward.setOnTouchListener(SingleButtonInputHandler(frontendInputHandler, Input.FAST_FORWARD, false))
        setupSoftInput()
        setupInputHandling()

        binding.imageTouchToggle.setOnClickListener {
            if (binding.layoutInputButtons.visibility == View.VISIBLE) {
                binding.layoutInputButtons.visibility = View.INVISIBLE
                binding.imageTouchToggle.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_touch_disabled))
            } else {
                binding.layoutInputButtons.visibility = View.VISIBLE
                binding.imageTouchToggle.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_touch_enabled))
            }
        }

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

    private fun setupSoftInput() {
        if (settingsRepository.showSoftInput()) {
            val opacity = settingsRepository.getSoftInputOpacity()
            val alpha = opacity / 100f

            binding.layoutInputButtons.visibility = View.VISIBLE
            binding.layoutInputButtons.alpha = alpha
            binding.imageTouchToggle.visibility = View.VISIBLE
            binding.imageTouchToggle.alpha = alpha
        } else {
            binding.layoutInputButtons.visibility = View.GONE
            binding.imageTouchToggle.visibility = View.GONE
        }
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

    override fun onRendererSizeChanged(width: Int, height: Int) {
        runOnUiThread {
            val dsAspectRatio = 192 / 256f
            val screenWidth = (width - dsRenderer.margin * 2).toInt()
            val screenHeight = (screenWidth * dsAspectRatio).toInt()

            val params = RelativeLayout.LayoutParams(screenWidth, screenHeight)
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            params.bottomMargin = dsRenderer.bottom.toInt()
            params.leftMargin = dsRenderer.margin.toInt()
            params.rightMargin = dsRenderer.margin.toInt()

            binding.viewInputArea.layoutParams = params
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
        val errorBody = "" +
                "* **Device model:** ${Build.MODEL}\n" +
                "* **Android version:** Android API ${Build.VERSION.SDK_INT}\n\n" +
                "**Problem:**  \n" +
                "Insert a small description of the problem.\n\n" +
                "**Stack trace:**  \n" +
                "```\n" +
                e.stackTraceToString() +
                "\n```"

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