package me.magnum.melonds.ui.emulator

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.widget.ConstraintLayout
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
import androidx.recyclerview.widget.LinearLayoutManager
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.R
import me.magnum.melonds.common.PermissionHandler
import me.magnum.melonds.common.runtime.FrameBufferProvider
import me.magnum.melonds.databinding.ActivityEmulatorBinding
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.FpsCounterPosition
import me.magnum.melonds.domain.model.LayoutComponent
import me.magnum.melonds.domain.model.Orientation
import me.magnum.melonds.domain.model.Rom
import me.magnum.melonds.domain.model.SaveStateSlot
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.extensions.insetsControllerCompat
import me.magnum.melonds.extensions.parcelable
import me.magnum.melonds.extensions.setLayoutOrientation
import me.magnum.melonds.parcelables.RomInfoParcelable
import me.magnum.melonds.parcelables.RomParcelable
import me.magnum.melonds.ui.cheats.CheatsActivity
import me.magnum.melonds.impl.emulator.LifecycleOwnerProvider
import me.magnum.melonds.ui.emulator.input.FrontendInputHandler
import me.magnum.melonds.ui.emulator.input.INativeInputListener
import me.magnum.melonds.ui.emulator.input.InputProcessor
import me.magnum.melonds.ui.emulator.input.MelonTouchHandler
import me.magnum.melonds.ui.emulator.model.EmulatorState
import me.magnum.melonds.ui.emulator.model.EmulatorUiEvent
import me.magnum.melonds.ui.emulator.model.PauseMenu
import me.magnum.melonds.ui.emulator.model.PopupEvent
import me.magnum.melonds.ui.emulator.model.RuntimeInputLayoutConfiguration
import me.magnum.melonds.ui.emulator.model.ToastEvent
import me.magnum.melonds.ui.emulator.rewind.EdgeSpacingDecorator
import me.magnum.melonds.ui.emulator.rewind.RewindSaveStateAdapter
import me.magnum.melonds.ui.emulator.rewind.model.RewindWindow
import me.magnum.melonds.ui.emulator.rom.SaveStateListAdapter
import me.magnum.melonds.ui.emulator.ui.AchievementPopupUi
import me.magnum.melonds.ui.emulator.ui.RAIntegrationEventUi
import me.magnum.melonds.ui.settings.SettingsActivity
import me.magnum.melonds.ui.theme.MelonTheme
import java.text.SimpleDateFormat
import javax.inject.Inject

@AndroidEntryPoint
class EmulatorActivity : AppCompatActivity() {
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

    @Inject
    lateinit var lifecycleOwnerProvider: LifecycleOwnerProvider

    private lateinit var dsRenderer: DSRenderer
    private lateinit var melonTouchHandler: MelonTouchHandler
    private lateinit var nativeInputListener: INativeInputListener
    private val frontendInputHandler = object : FrontendInputHandler() {
        private var fastForwardEnabled = false

        override fun onSoftInputTogglePressed() {
            binding.viewLayoutControls.toggleSoftInputVisibility()
        }

        override fun onPausePressed() {
            viewModel.pauseEmulator(true)
        }

        override fun onFastForwardPressed() {
            fastForwardEnabled = !fastForwardEnabled
            MelonEmulator.setFastForwardEnabled(fastForwardEnabled)
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
    private var resumeEmulatorOnActivityResume = true
    private var emulatorReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleOwnerProvider.setCurrentLifecycleOwner(this)
        binding = ActivityEmulatorBinding.inflate(layoutInflater)
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(binding.root)
        setupFullscreen()

        onBackPressedDispatcher.addCallback(backPressedCallback)

        melonTouchHandler = MelonTouchHandler()
        dsRenderer = DSRenderer(frameBufferProvider.frameBuffer(), this)
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
                                    .offset(y = (popupOffset * (popupHeight ?: Int.MAX_VALUE)).dp)
                                    .onSizeChanged { popupHeight = it.height },
                                achievement = currentPopupEvent.achievement,
                            )
                        }
                        is PopupEvent.RAIntegrationPopup -> {
                            RAIntegrationEventUi(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .offset(y = (popupOffset * (popupHeight ?: Int.MAX_VALUE)).dp)
                                    .onSizeChanged { popupHeight = it.height },
                                event = currentPopupEvent.event,
                            )
                        }
                        null -> {
                            // Do nothing
                        }
                    }
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
                viewModel.currentFps.collectLatest {
                    if (it == null) {
                        binding.textFps.text = ""
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
                        ToastEvent.ResetFailed -> R.string.failed_reset_emulation to Toast.LENGTH_SHORT
                        ToastEvent.RewindNotEnabled -> R.string.rewind_not_enabled to Toast.LENGTH_SHORT
                        ToastEvent.RewindNotAvailableWhileRAHardcoreModeEnabled -> R.string.rewind_unavailable_ra_hardcore_enabled to Toast.LENGTH_LONG
                        ToastEvent.StateLoadFailed -> R.string.failed_load_state to Toast.LENGTH_SHORT
                        ToastEvent.StateSaveFailed -> R.string.failed_save_state to Toast.LENGTH_SHORT
                        ToastEvent.StateStateDoesNotExist -> R.string.cant_load_empty_slot to Toast.LENGTH_SHORT
                        ToastEvent.CannotUseSaveStatesWhenRAHardcoreIsEnabled -> R.string.save_states_unavailable_ra_hardcore_enabled to Toast.LENGTH_LONG
                        ToastEvent.CannotLoadStateWhenRunningFirmware,
                        ToastEvent.CannotSaveStateWhenRunningFirmware -> R.string.save_states_not_supported to Toast.LENGTH_LONG
                    }

                    Toast.makeText(this@EmulatorActivity, message, duration).show()
                }
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.uiEvent.collectLatest {
                    when (it) {
                        EmulatorUiEvent.CloseEmulator -> finish()
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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        if (viewModel.emulatorState.value.isRunning()) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            resumeEmulatorOnActivityResume = false
            viewModel.pauseEmulator(false)
            backPressedCallback.isEnabled = false

            AlertDialog.Builder(this)
                    .setTitle(getString(R.string.title_emulator_running))
                    .setMessage(getString(R.string.message_stop_emulation))
                    .setPositiveButton(R.string.ok) { _, _ ->
                        viewModel.stopEmulator()
                        resumeEmulatorOnActivityResume = true
                        setIntent(intent)
                        launchEmulator()
                    }
                    .setNegativeButton(R.string.no) { dialog, _ ->
                        dialog.cancel()
                    }
                    .setOnCancelListener {
                        resumeEmulatorOnActivityResume = true
                        backPressedCallback.isEnabled = true
                        viewModel.resumeEmulator()
                    }
                    .show()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.surfaceMain.onResume()

        if (resumeEmulatorOnActivityResume) {
            disableScreenTimeOut()
            viewModel.resumeEmulator()
        }
    }

    private fun launchEmulator() {
        val extras = intent?.extras
        val bootFirmwareOnly = extras?.getBoolean(KEY_BOOT_FIRMWARE_ONLY) ?: false

        disableScreenTimeOut()
        if (bootFirmwareOnly) {
            val consoleTypeParameter = extras?.getInt(KEY_BOOT_FIRMWARE_CONSOLE, -1)
            if (consoleTypeParameter == null || consoleTypeParameter == -1) {
                throw RuntimeException("No console type specified")
            }

            val firmwareConsoleType = ConsoleType.values()[consoleTypeParameter]
            viewModel.loadFirmware(firmwareConsoleType)
        } else {
            val romParcelable = extras?.parcelable(KEY_ROM) as RomParcelable?

            if (romParcelable?.rom != null) {
                viewModel.loadRom(romParcelable.rom)
            } else {
                if (extras?.containsKey(KEY_PATH) == true) {
                    val romPath = extras.getString(KEY_PATH)!!
                    viewModel.loadRom(romPath)
                } else if (extras?.containsKey(KEY_URI) == true) {
                    val romUri = extras.getString(KEY_URI)!!
                    viewModel.loadRom(romUri.toUri())
                } else {
                    throw RuntimeException("No ROM was specified")
                }
            }
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
            viewModel.pauseEmulator(true)
        }
    }

    private fun showPauseMenu(pauseMenu: PauseMenu) {
        val options = Array(pauseMenu.options.size) {
            getString(pauseMenu.options[it].textResource)
        }

        resumeEmulatorOnActivityResume = false
        enableScreenTimeOut()
        AlertDialog.Builder(this)
                .setTitle(R.string.pause)
                .setItems(options) { _, which ->
                    val selectedOption = pauseMenu.options[which]
                    viewModel.onPauseMenuOptionSelected(selectedOption)
                    disableScreenTimeOut()
                }
                .setOnCancelListener {
                    viewModel.resumeEmulator()
                    resumeEmulatorOnActivityResume = true
                    disableScreenTimeOut()
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
        if (!isRewindWindowOpen() && nativeInputListener.onKeyEvent(event))
            return true

        return super.dispatchKeyEvent(event)
    }

    private fun isRewindWindowOpen(): Boolean {
        return binding.root.currentState == R.id.rewind_visible
    }

    private fun showSaveStateSlotsDialog(slots: List<SaveStateSlot>, onSlotPicked: (SaveStateSlot) -> Unit) {
        val dateFormatter = SimpleDateFormat("EEE, dd MMM yyyy", ConfigurationCompat.getLocales(resources.configuration)[0])
        val timeFormatter = SimpleDateFormat("kk:mm:ss", ConfigurationCompat.getLocales(resources.configuration)[0])
        var dialog: AlertDialog? = null
        var adapter: SaveStateListAdapter? = null

        adapter = SaveStateListAdapter(slots, picasso, dateFormatter, timeFormatter, {
            disableScreenTimeOut()
            dialog?.cancel()
            onSlotPicked(it)
        }) {
            viewModel.deleteSaveStateSlot(it)?.let { newSlots ->
                adapter?.updateSaveStateSlots(newSlots)
            }
        }

        enableScreenTimeOut()
        dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.save_slot))
            .setAdapter(adapter) { _, _ ->
            }
            .setNegativeButton(R.string.cancel) { _dialog, _ ->
                _dialog.cancel()
            }
            .setOnCancelListener {
                viewModel.resumeEmulator()
                disableScreenTimeOut()
            }
            .show()
    }

    private fun showRomLoadErrorDialog() {
        enableScreenTimeOut()
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
        enableScreenTimeOut()
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
        enableScreenTimeOut()
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
        enableScreenTimeOut()
        binding.root.transitionToState(R.id.rewind_visible)
        rewindSaveStateAdapter.setRewindWindow(rewindWindow)
    }

    private fun closeRewindWindow() {
        disableScreenTimeOut()
        binding.root.transitionToState(R.id.rewind_hidden)
        viewModel.resumeEmulator()
    }

    override fun onPause() {
        super.onPause()
        enableScreenTimeOut()
        binding.surfaceMain.onPause()
        viewModel.pauseEmulator(false)
    }
}