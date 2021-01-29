package me.magnum.melonds.ui.emulator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.Single
import io.reactivex.SingleObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_emulator.*
import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.MelonEmulator.LoadResult
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.*
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.parcelables.RomParcelable
import me.magnum.melonds.ui.settings.SettingsActivity
import me.magnum.melonds.ui.emulator.DSRenderer.RendererListener
import me.magnum.melonds.ui.emulator.input.*
import me.magnum.melonds.utils.FileUtils
import me.magnum.melonds.utils.isMicrophonePermissionGranted
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import javax.inject.Inject

@AndroidEntryPoint
class EmulatorActivity : AppCompatActivity(), RendererListener {
    companion object {
        const val KEY_ROM = "rom"
        const val KEY_PATH = "PATH"

        init {
            System.loadLibrary("melonDS-android-frontend")
        }
    }

    private class RomLoadFailedException : Exception("Failed to load ROM")

    private enum class PauseMenuOptions(val textResource: Int) {
        SETTINGS(R.string.settings),
        SAVE_STATE(R.string.save_state),
        LOAD_STATE(R.string.load_state),
        EXIT(R.string.exit);
    }

    private val viewModel: EmulatorViewModel by viewModels()
    @Inject lateinit var settingsRepository: SettingsRepository

    private lateinit var loadedRom: Rom
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
        val newEmulatorConfiguration = getEmulatorConfigurationForRom(loadedRom)
        MelonEmulator.updateEmulatorConfiguration(newEmulatorConfiguration)
        dsRenderer.updateRendererConfiguration(newEmulatorConfiguration.rendererConfiguration)
        setupSoftInput()
        setupInputHandling()
    }

    private var emulatorReady = false
    private var emulatorPaused = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setupFullscreen()
        setContentView(R.layout.activity_emulator)

        melonTouchHandler = MelonTouchHandler()
        dsRenderer = DSRenderer(buildRendererConfiguration())
        dsRenderer.setRendererListener(this)
        surfaceMain.apply {
            setEGLContextClientVersion(2)
            setRenderer(dsRenderer)
        }

        textFps.visibility = View.INVISIBLE

        viewInputArea.setOnTouchListener(TouchscreenInputHandler(melonTouchHandler))
        imageDpad.setOnTouchListener(DpadInputHandler(melonTouchHandler))
        imageButtons.setOnTouchListener(ButtonsInputHandler(melonTouchHandler))
        imageButtonL.setOnTouchListener(SingleButtonInputHandler(melonTouchHandler, Input.L))
        imageButtonR.setOnTouchListener(SingleButtonInputHandler(melonTouchHandler, Input.R))
        imageButtonSelect.setOnTouchListener(SingleButtonInputHandler(melonTouchHandler, Input.SELECT))
        imageButtonStart.setOnTouchListener(SingleButtonInputHandler(melonTouchHandler, Input.START))
        imageButtonLid.setOnTouchListener(SingleButtonInputHandler(melonTouchHandler, Input.HINGE, true))
        imageButtonFastForward.setOnTouchListener(SingleButtonInputHandler(frontendInputHandler, Input.FAST_FORWARD, false))
        setupSoftInput()
        setupInputHandling()

        imageTouchToggle.setOnClickListener {
            if (layoutInputButtons.visibility == View.VISIBLE) {
                layoutInputButtons.visibility = View.INVISIBLE
                imageTouchToggle.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_touch_disabled))
            } else {
                layoutInputButtons.visibility = View.VISIBLE
                imageTouchToggle.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_touch_enabled))
            }
        }

        launchEmulator()
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
        surfaceMain.onResume()

        if (emulatorReady && !emulatorPaused)
            MelonEmulator.resumeEmulation()
    }

    private fun launchEmulator() {
        val bundle = intent.extras ?: throw NullPointerException("No ROM was specified")

        val romParcelable = bundle.getParcelable(KEY_ROM) as RomParcelable?

        val romLoader = if (romParcelable?.rom != null)
            Single.just(romParcelable.rom)
        else {
            val romPath = bundle.getString(KEY_PATH) ?: throw NullPointerException("No ROM was specified")
            viewModel.getRomAtPath(romPath)
        }

        // Force view model resolution
        viewModel.let {  }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        romLoader.flatMap {
            loadedRom = it
            Single.create<LoadResult> { emitter ->
                MelonEmulator.setupEmulator(getEmulatorConfigurationForRom(it), assets)

                val romPath = FileUtils.getAbsolutePathFromSAFUri(this, it.uri) ?: throw RomLoadFailedException()
                val showBios = settingsRepository.showBootScreen()
                val sramPath = getSRAMPath(it.uri)

                val gbaCartPath = FileUtils.getAbsolutePathFromSAFUri(this, it.config.gbaCartPath)
                val gbaSavePath = FileUtils.getAbsolutePathFromSAFUri(this, it.config.gbaSavePath)
                val loadResult = MelonEmulator.loadRom(romPath, sramPath, !showBios, it.config.loadGbaCart(), gbaCartPath, gbaSavePath)
                if (loadResult === LoadResult.NDS_FAILED)
                    throw RomLoadFailedException()

                MelonEmulator.startEmulation()
                emitter.onSuccess(loadResult)
            }
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : SingleObserver<LoadResult> {
                    override fun onSubscribe(disposable: Disposable) {
                        textFps.visibility = View.GONE
                        textLoading.visibility = View.VISIBLE
                    }

                    override fun onSuccess(loadResult: LoadResult) {
                        if (loadResult === LoadResult.SUCCESS_GBA_FAILED)
                            Toast.makeText(this@EmulatorActivity, R.string.error_load_gba_rom, Toast.LENGTH_SHORT).show()

                        textFps.visibility = View.VISIBLE
                        textLoading.visibility = View.GONE
                        emulatorReady = true
                    }

                    override fun onError(e: Throwable) {
                        Toast.makeText(this@EmulatorActivity, R.string.error_load_rom, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                })
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        setupFullscreen()
    }

    private fun getEmulatorConfigurationForRom(rom: Rom): EmulatorConfiguration {
        val emulatorConfiguration = viewModel.getEmulatorConfigurationForRom(rom)

        // Use BLOW mic source if mic permission is not granted
        return if (emulatorConfiguration.micSource == MicSource.DEVICE && !isMicrophonePermissionGranted(this)) {
            emulatorConfiguration.copy(micSource = MicSource.BLOW)
        } else {
            emulatorConfiguration
        }
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

            layoutInputButtons.visibility = View.VISIBLE
            layoutInputButtons.alpha = alpha
            imageTouchToggle.visibility = View.VISIBLE
            imageTouchToggle.alpha = alpha
        } else {
            layoutInputButtons.visibility = View.GONE
            imageTouchToggle.visibility = View.GONE
        }
    }

    private fun setupInputHandling() {
        nativeInputListener = InputProcessor(settingsRepository.getControllerConfiguration(), melonTouchHandler, frontendInputHandler)
    }

    override fun onBackPressed() {
        this.pauseEmulation()
    }

    private fun pauseEmulation() {
        emulatorPaused = true
        val values = PauseMenuOptions.values()
        val options = Array(values.size) { i -> getString(values[i].textResource) }

        MelonEmulator.pauseEmulation()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        AlertDialog.Builder(this)
                .setTitle(R.string.pause)
                .setItems(options) { _, which ->
                    when (values[which]) {
                        PauseMenuOptions.SETTINGS -> {
                            // Allow emulator to resume once the user returns from Settings
                            emulatorPaused = false
                            val settingsIntent = Intent(this@EmulatorActivity, SettingsActivity::class.java)
                            settingsLauncher.launch(Intent(settingsIntent))
                        }
                        PauseMenuOptions.SAVE_STATE -> pickSaveStateSlot {
                            if (!MelonEmulator.saveState(it.path))
                                Toast.makeText(this@EmulatorActivity, getString(R.string.failed_save_state), Toast.LENGTH_SHORT).show()

                            resumeEmulation()
                        }
                        PauseMenuOptions.LOAD_STATE -> pickSaveStateSlot {
                            if (!it.exists) {
                                Toast.makeText(this@EmulatorActivity, getString(R.string.cant_load_empty_slot), Toast.LENGTH_SHORT).show()
                            } else {
                                if (!MelonEmulator.loadState(it.path))
                                    Toast.makeText(this@EmulatorActivity, getString(R.string.failed_load_state), Toast.LENGTH_SHORT).show()
                            }

                            resumeEmulation()
                        }
                        PauseMenuOptions.EXIT -> finish()
                    }
                }
                .setOnCancelListener { resumeEmulation()}
                .show()
    }

    private fun resumeEmulation() {
        emulatorPaused = false
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        MelonEmulator.resumeEmulation()
    }

    private fun getSRAMPath(romUri: Uri): String {
        val romPath = FileUtils.getAbsolutePathFromSAFUri(this, romUri)
        val romFile = File(romPath)

        val sramDir = if (settingsRepository.saveNextToRomFile()) {
            romFile.parent
        } else {
            val sramDirUri = settingsRepository.getSaveFileDirectory()
            if (sramDirUri != null)
                FileUtils.getAbsolutePathFromSAFUri(this, sramDirUri) ?: romFile.parent
            else {
                // If no directory is set, revert to using the ROM's directory
                romFile.parent
            }
        }

        val nameWithoutExtension = romFile.nameWithoutExtension
        val sramFileName = "$nameWithoutExtension.sav"
        return File(sramDir, sramFileName).absolutePath
    }

    override fun onRendererSizeChanged(width: Int, height: Int) {
        runOnUiThread {
            val dsAspectRatio = 192 / 256f
            val screenHeight = (width * dsAspectRatio).toInt()

            val params = RelativeLayout.LayoutParams(width, screenHeight)
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            params.bottomMargin = dsRenderer.bottom.toInt()

            viewInputArea.layoutParams = params
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
        runOnUiThread { textFps.text = getString(R.string.info_fps, fps) }
    }

    private fun pickSaveStateSlot(onSlotPicked: (SaveStateSlot) -> Unit) {
        val dateFormatter = SimpleDateFormat("EEE, dd MMMM yyyy kk:mm:ss", ConfigurationCompat.getLocales(resources.configuration)[0])
        val slots = viewModel.getRomSaveStateSlots(loadedRom)
        val options = slots.map { "${it.slot}. ${if (it.exists) dateFormatter.format(it.lastUsedDate!!) else getString(R.string.empty_slot)}" }.toTypedArray()

        AlertDialog.Builder(this)
                .setTitle(getString(R.string.save_slot))
                .setItems(options) { _, which ->
                    onSlotPicked(slots[which])
                }
                .setNegativeButton(R.string.cancel) { dialog, _ ->
                    dialog.cancel()
                }
                .setOnCancelListener { resumeEmulation() }
                .show()
    }

    override fun onPause() {
        super.onPause()
        surfaceMain.onPause()

        if (emulatorReady && !emulatorPaused)
            MelonEmulator.pauseEmulation()
    }

    override fun onDestroy() {
        if (emulatorReady)
            MelonEmulator.stopEmulation()

        super.onDestroy()
    }
}