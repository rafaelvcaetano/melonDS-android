package me.magnum.melonds.ui.layouteditor

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.view.Display
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.Rect
import me.magnum.melonds.domain.model.layout.ScreenFold
import me.magnum.melonds.domain.model.ui.Orientation
import me.magnum.melonds.extensions.insetsControllerCompat
import me.magnum.melonds.extensions.setLayoutOrientation
import me.magnum.melonds.impl.ScreenUnitsConverter
import me.magnum.melonds.impl.layout.DeviceLayoutDisplayMapper
import me.magnum.melonds.impl.layout.SecondaryDisplaySelector
import me.magnum.melonds.ui.backgrounds.BackgroundsActivity
import me.magnum.melonds.ui.layouteditor.model.LayoutTarget
import me.magnum.melonds.ui.layouteditor.model.ScreenEditorState
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class LayoutEditorActivity : AppCompatActivity() {
    companion object {
        const val KEY_LAYOUT_ID = "layout_id"
        const val KEY_IS_EXTERNAL = "is_external"
    }

    enum class MenuOption(@StringRes val stringRes: Int) {
        PROPERTIES(R.string.properties),
        BACKGROUNDS(R.string.background),
        REVERT(R.string.revert_changes),
        RESET(R.string.reset_default),
        SAVE_AND_EXIT(R.string.save_and_exit),
        EXIT_WITHOUT_SAVING(R.string.exit_without_saving)
    }

    @Inject
    lateinit var screenUnitsConverter: ScreenUnitsConverter
    @Inject
    lateinit var secondaryDisplaySelector: SecondaryDisplaySelector
    @Inject
    lateinit var deviceLayoutDisplayMapper: DeviceLayoutDisplayMapper
    @Inject
    lateinit var picasso: Picasso

    private val viewModel: LayoutEditorViewModel by viewModels()
    private lateinit var layoutEditorManager: LayoutEditorManagerView
    private lateinit var handler: Handler
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            updateDisplays()
        }

        override fun onDisplayRemoved(displayId: Int) {
            updateDisplays()
        }

        override fun onDisplayChanged(displayId: Int) {
            updateDisplays()
        }
    }

    private var externalLayoutEditorPresentation: ExternalLayoutEditorPresentation? = null
    private var savedExternalEditorState: ScreenEditorState? = null

    private val mainScreenBackgroundPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            val backgroundId = it.data?.getStringExtra(BackgroundsActivity.KEY_SELECTED_BACKGROUND_ID)?.let { UUID.fromString(it) }
            viewModel.setBackgroundPropertiesBackgroundId(LayoutTarget.MAIN_SCREEN, backgroundId)
        }
    }

    private val secondaryScreenBackgroundPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            val backgroundId = it.data?.getStringExtra(BackgroundsActivity.KEY_SELECTED_BACKGROUND_ID)?.let { UUID.fromString(it) }
            viewModel.setBackgroundPropertiesBackgroundId(LayoutTarget.SECONDARY_SCREEN, backgroundId)
        }
    }

    private val layoutEditorManagerListener = object : LayoutEditorManagerView.LayoutEditorManagerListener {
        override fun openBackgroundPicker(layoutTarget: LayoutTarget, selectedBackgroundId: UUID?) {
            storeLayoutChanges()
            val intent = Intent(this@LayoutEditorActivity, BackgroundsActivity::class.java).apply {
                putExtra(BackgroundsActivity.KEY_INITIAL_BACKGROUND_ID, selectedBackgroundId?.toString())
            }
            when (layoutTarget) {
                LayoutTarget.MAIN_SCREEN -> mainScreenBackgroundPickerLauncher.launch(intent)
                LayoutTarget.SECONDARY_SCREEN -> secondaryScreenBackgroundPickerLauncher.launch(intent)
            }
        }

        override fun onStoreLayoutChanges() {
            storeLayoutChanges()
        }

        override fun onSaveLayoutAndExit() {
            saveLayoutAndExit()
        }

        override fun onExit() {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handler = Handler(mainLooper)

        layoutEditorManager = LayoutEditorManagerView(LayoutTarget.MAIN_SCREEN, picasso, null, this).apply {
            setBackgroundColor(Color.BLACK)
            listener = layoutEditorManagerListener
        }
        setContentView(layoutEditorManager)

        onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                layoutEditorManager.openMenu()
            }
        })

        layoutEditorManager.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val oldWith = oldRight - oldLeft
            val oldHeight = oldBottom - oldTop

            val viewWidth = right - left
            val viewHeight = bottom - top

            if (viewWidth != oldWith || viewHeight != oldHeight) {
                storeLayoutChanges()
                viewModel.setCurrentUiSize(viewWidth, viewHeight)
            }
        }

        setupFullscreen()
        updateOrientation(resources.configuration)

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentLayout.collect {
                    if (it == null) {
                        layoutEditorManager.layoutEditorView.destroyLayout()
                        externalLayoutEditorPresentation?.layoutEditorManager?.layoutEditorView?.destroyLayout()
                    } else {
                        handler.removeCallbacksAndMessages(null)
                        handler.post {
                            layoutEditorManager.layoutEditorView.instantiateLayout(it.layout)
                            externalLayoutEditorPresentation?.instantiateLayout(it.layout)
                            setLayoutOrientation(it.orientation)
                        }
                    }
                }
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.mainScreenBackground.collect {
                    it?.let {
                        layoutEditorManager.updateBackground(it)
                    }
                }
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.secondaryScreenBackground.collect {
                    it?.let {
                        externalLayoutEditorPresentation?.layoutEditorManager?.updateBackground(it)
                    }
                }
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@LayoutEditorActivity).windowLayoutInfo(this@LayoutEditorActivity).collect { info ->
                    val folds = info.displayFeatures.mapNotNull {
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

                    storeLayoutChanges()
                    viewModel.setScreenFolds(folds)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        getSystemService<DisplayManager>()?.registerDisplayListener(displayListener, null)
    }

    override fun onResume() {
        super.onResume()
        updateDisplays()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        setupFullscreen()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        storeLayoutChanges()
        updateOrientation(newConfig)
        handler.post {
            updateDisplays()
        }
    }

    private fun updateOrientation(configuration: Configuration) {
        val orientation = if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Orientation.PORTRAIT
        } else {
            Orientation.LANDSCAPE
        }
        viewModel.setCurrentSystemOrientation(orientation)
    }

    private fun updateDisplays() {
        val currentDisplay = ContextCompat.getDisplayOrDefault(this)
        val secondaryDisplay = secondaryDisplaySelector.getSecondaryDisplay(this)

        val displays = deviceLayoutDisplayMapper.mapDisplaysToLayoutDisplays(currentDisplay, secondaryDisplay)
        viewModel.setConnectedDisplays(displays)

        showExternalLayoutEditor(secondaryDisplay)
    }

    private fun showExternalLayoutEditor(secondaryDisplay: Display?) {
        if (externalLayoutEditorPresentation?.display?.displayId == secondaryDisplay?.displayId) {
            return
        }

        externalLayoutEditorPresentation?.dismiss()
        externalLayoutEditorPresentation = null

        if (secondaryDisplay != null) {
            externalLayoutEditorPresentation = ExternalLayoutEditorPresentation(picasso, this, secondaryDisplay, layoutEditorManagerListener, savedExternalEditorState).apply {
                setOnShowListener {
                    val currentConfiguration = viewModel.currentLayout.value
                    if (currentConfiguration != null) {
                        instantiateLayout(currentConfiguration.layout)
                    }
                }
                show()
            }
        }
    }

    private fun storeLayoutChanges() {
        val primaryDisplayLayoutComponents = if (layoutEditorManager.layoutEditorView.isModifiedByUser()) {
            layoutEditorManager.layoutEditorView.buildCurrentLayout()
        } else {
            null
        }

        val externalPresentationLayoutView = externalLayoutEditorPresentation?.layoutEditorManager?.layoutEditorView
        val secondaryDisplayLayoutComponents = if (externalPresentationLayoutView?.isModifiedByUser() == true) {
            externalPresentationLayoutView.buildCurrentLayout()
        } else {
            null
        }
        viewModel.saveLayoutToCurrentConfiguration(primaryDisplayLayoutComponents, secondaryDisplayLayoutComponents)
    }

    private fun setupFullscreen() {
        window.insetsControllerCompat?.let {
            it.hide(WindowInsetsCompat.Type.navigationBars())
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun saveLayoutAndExit() {
        storeLayoutChanges()
        viewModel.saveCurrentLayout()
        finish()
    }

    override fun onStop() {
        super.onStop()
        getSystemService<DisplayManager>()?.unregisterDisplayListener(displayListener)
        storeLayoutChanges()
        externalLayoutEditorPresentation?.let {
            savedExternalEditorState = it.saveEditorState()
            it.dismiss()
        }
        externalLayoutEditorPresentation = null
    }

    override fun onDestroy() {
        super.onDestroy()
        picasso.cancelRequest(layoutEditorManager.imageBackground)
        externalLayoutEditorPresentation?.layoutEditorManager?.imageBackground?.let {
            picasso.cancelRequest(it)
        }
    }
}