package me.magnum.melonds.ui.layouteditor

import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.KeyEvent
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ActivityLayoutEditorBinding
import me.magnum.melonds.domain.model.layout.LayoutComponent
import me.magnum.melonds.domain.model.Rect
import me.magnum.melonds.domain.model.RuntimeBackground
import me.magnum.melonds.domain.model.ui.Orientation
import me.magnum.melonds.domain.model.layout.ScreenFold
import me.magnum.melonds.extensions.insetsControllerCompat
import me.magnum.melonds.extensions.setBackgroundMode
import me.magnum.melonds.extensions.setLayoutOrientation
import me.magnum.melonds.impl.ScreenUnitsConverter
import me.magnum.melonds.ui.common.TextInputDialog
import me.magnum.melonds.utils.getLayoutComponentName
import javax.inject.Inject

@AndroidEntryPoint
class LayoutEditorActivity : AppCompatActivity() {
    companion object {
        const val KEY_LAYOUT_ID = "layout_id"
        const val KEY_IS_EXTERNAL = "is_external"

        private const val CONTROLS_SLIDE_ANIMATION_DURATION_MS = 100L
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
    lateinit var picasso: Picasso

    private val viewModel: LayoutEditorViewModel by viewModels()
    private lateinit var binding: ActivityLayoutEditorBinding
    private lateinit var handler: Handler
    private var areBottomControlsShown = true
    private var areScalingControlsShown = true
    private var isExternalLayout = false
    private var selectedViewMinSize = 0
    private var currentWidthScale = 0f
    private var currentHeightScale = 0f
    private var selectedViewIsScreen = false
    private var selectedScreenComponent: LayoutComponent? = null
    private var keepAspectRatio = false
    private var keepAspectRatioTop = false
    private var keepAspectRatioBottom = false

    /** When true, skip the next layout update from the ViewModel since the
     *  current views already reflect the stored changes. */
    private var ignoreNextLayoutUpdate = false

    private val dsRatio = 256f / 192f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLayoutEditorBinding.inflate(layoutInflater)
        handler = Handler(mainLooper)
        setContentView(binding.root)

        isExternalLayout = intent?.getBooleanExtra(KEY_IS_EXTERNAL, false) ?: false

        binding.textLayoutType.text = if (isExternalLayout) {
            getString(R.string.editing_external_layout)
        } else {
            getString(R.string.editing_internal_layout)
        }

        onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                openMenu()
            }
        })

        binding.buttonAddButton.setOnClickListener {
            openButtonsMenu()
        }
        binding.buttonMenu.setOnClickListener {
            openMenu()
        }
        binding.buttonDeleteButton.setOnClickListener {
            binding.viewLayoutEditor.deleteSelectedView()
        }

        binding.viewLayoutEditor.setLayoutComponentViewBuilderFactory(EditorLayoutComponentViewBuilderFactory())
        binding.viewLayoutEditor.setOnClickListener {
            if (areBottomControlsShown)
                hideBottomControls()
            else
                showBottomControls()
        }
        binding.viewLayoutEditor.setOnViewSelectedListener { view, widthScale, heightScale, maxWidth, maxHeight, minSize ->
            // Persist any pending changes from the previous selection so the
            // current layout state is always up to date.
            storeLayoutChanges()

            hideBottomControls()
            // Force the scaling controls to restart so the new view always
            // receives fresh listeners even if they were already visible.
            hideScalingControls(false)
            selectedViewIsScreen = view.component.isScreen()
            selectedScreenComponent = view.component
            keepAspectRatio = when (view.component) {
                LayoutComponent.TOP_SCREEN -> keepAspectRatioTop
                LayoutComponent.BOTTOM_SCREEN -> keepAspectRatioBottom
                else -> false
            }
            showScalingControls(
                widthScale,
                heightScale,
                maxWidth,
                maxHeight,
                minSize,
                selectedViewIsScreen,
                view.baseAlpha,
                view.onTop,
            )
        }
        binding.viewLayoutEditor.setOnViewDeselectedListener {
            storeLayoutChanges()
            hideScalingControls()
        }
        binding.seekBarWidth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentWidthScale = progress / binding.seekBarWidth.max.toFloat()
                binding.textWidth.text = ((binding.seekBarWidth.max - selectedViewMinSize) * currentWidthScale + selectedViewMinSize).toInt().toString()
                if (keepAspectRatio && selectedViewIsScreen && fromUser) {
                    val width = (binding.seekBarWidth.max - selectedViewMinSize) * currentWidthScale + selectedViewMinSize
                    val height = width / dsRatio
                    currentHeightScale = ((height - selectedViewMinSize) / (binding.seekBarHeight.max - selectedViewMinSize).toFloat()).coerceIn(0f, 1f)
                    binding.seekBarHeight.progress = (currentHeightScale * binding.seekBarHeight.max).toInt()
                    binding.textHeight.text = ((binding.seekBarHeight.max - selectedViewMinSize) * currentHeightScale + selectedViewMinSize).toInt().toString()
                }
                binding.viewLayoutEditor.scaleSelectedView(currentWidthScale, currentHeightScale)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })

        binding.seekBarHeight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentHeightScale = progress / binding.seekBarHeight.max.toFloat()
                binding.textHeight.text = ((binding.seekBarHeight.max - selectedViewMinSize) * currentHeightScale + selectedViewMinSize).toInt().toString()
                if (keepAspectRatio && selectedViewIsScreen && fromUser) {
                    val height = (binding.seekBarHeight.max - selectedViewMinSize) * currentHeightScale + selectedViewMinSize
                    val width = height * dsRatio
                    currentWidthScale = ((width - selectedViewMinSize) / (binding.seekBarWidth.max - selectedViewMinSize).toFloat()).coerceIn(0f, 1f)
                    binding.seekBarWidth.progress = (currentWidthScale * binding.seekBarWidth.max).toInt()
                    binding.textWidth.text = ((binding.seekBarWidth.max - selectedViewMinSize) * currentWidthScale + selectedViewMinSize).toInt().toString()
                }
                binding.viewLayoutEditor.scaleSelectedView(currentWidthScale, currentHeightScale)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })

        binding.seekBarAlpha.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val alpha = progress / 100f
                binding.viewLayoutEditor.setSelectedViewAlpha(alpha)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.checkboxKeepRatio.setOnCheckedChangeListener { _, isChecked ->
            keepAspectRatio = isChecked
            when (selectedScreenComponent) {
                LayoutComponent.TOP_SCREEN -> keepAspectRatioTop = isChecked
                LayoutComponent.BOTTOM_SCREEN -> keepAspectRatioBottom = isChecked
                else -> {}
            }
        }

        binding.checkboxAboveScreen.setOnCheckedChangeListener { _, isChecked ->
            binding.viewLayoutEditor.setSelectedScreenOnTop(isChecked)
            storeLayoutChanges()
        }

        binding.viewLayoutEditor.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
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
        hideScalingControls(false)
        updateOrientation(resources.configuration)

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentLayout.collect {
                    if (ignoreNextLayoutUpdate) {
                        ignoreNextLayoutUpdate = false
                        return@collect
                    }

                    if (it == null) {
                        binding.viewLayoutEditor.destroyLayout()
                    } else {
                        Log.d("LayoutEditorActivity", "Instantiating layout. On main thread: ${mainLooper.isCurrentThread}")
                        handler.removeCallbacksAndMessages(null)
                        handler.post {
                            binding.viewLayoutEditor.instantiateLayout(it.layout)
                            setLayoutOrientation(it.orientation)
                        }
                    }
                }
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.background.collect {
                    it?.let {
                        updateBackground(it)
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyDown(keyCode, event)

        return if (binding.viewLayoutEditor.handleKeyDown(event)) {
            true
        } else {
            super.onKeyDown(keyCode, event)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        setupFullscreen()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        storeLayoutChanges()
        updateOrientation(newConfig)
    }

    private fun updateOrientation(configuration: Configuration) {
        val orientation = if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Orientation.PORTRAIT
        } else {
            Orientation.LANDSCAPE
        }
        viewModel.setCurrentSystemOrientation(orientation)
    }

    private fun storeLayoutChanges() {
        if (binding.viewLayoutEditor.isModifiedByUser()) {
            val layoutComponents = binding.viewLayoutEditor.buildCurrentLayout()
            ignoreNextLayoutUpdate = true
            viewModel.saveLayoutToCurrentConfiguration(layoutComponents)
        }
    }

    private fun setupFullscreen() {
        window.insetsControllerCompat?.let {
            it.hide(WindowInsetsCompat.Type.navigationBars())
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun updateBackground(background: RuntimeBackground) {
        picasso.load(background.background?.uri).into(binding.imageBackground, object : Callback {
            override fun onSuccess() {
                binding.imageBackground.setBackgroundMode(background.mode)
            }

            override fun onError(e: java.lang.Exception?) {
                e?.printStackTrace()
                Toast.makeText(this@LayoutEditorActivity, R.string.layout_background_load_failed, Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun showBottomControls(animate: Boolean = true) {
        if (areBottomControlsShown) {
            return
        }

        binding.layoutControls.clearAnimation()
        if (animate) {
            binding.layoutControls
                .animate()
                .y(binding.layoutControls.bottom.toFloat() - binding.layoutControls.height.toFloat())
                .setDuration(CONTROLS_SLIDE_ANIMATION_DURATION_MS)
                .withStartAction {
                    binding.layoutControls.isVisible = true
                }
                .start()
        } else {
            binding.layoutControls.isVisible = true
        }

        areBottomControlsShown = true
    }

    private fun hideBottomControls(animate: Boolean = true) {
        if (!areBottomControlsShown) {
            return
        }

        if (animate) {
            binding.layoutControls.clearAnimation()
            if (animate) {
                binding.layoutControls.animate()
                    .y(binding.layoutControls.bottom.toFloat())
                    .setDuration(CONTROLS_SLIDE_ANIMATION_DURATION_MS)
                    .withEndAction {
                        binding.layoutControls.isGone = true
                    }
                    .start()
            }
        } else {
            binding.layoutControls.isGone = true
        }

        areBottomControlsShown = false
    }

    private fun showScalingControls(
        widthScale: Float,
        heightScale: Float,
        maxWidth: Int,
        maxHeight: Int,
        minSize: Int,
        isScreen: Boolean,
        alpha: Float,
        onTop: Boolean,
        animate: Boolean = true,
    ) {
        binding.layoutScalingContainer.clearAnimation()

        binding.seekBarWidth.apply {
            max = maxWidth
            progress = (widthScale * maxWidth).toInt()
        }
        binding.textWidth.text = ((binding.seekBarWidth.max - minSize) * widthScale + minSize).toInt().toString()

        binding.seekBarHeight.apply {
            max = maxHeight
            progress = (heightScale * maxHeight).toInt()
        }
        binding.textHeight.text = ((binding.seekBarHeight.max - minSize) * heightScale + minSize).toInt().toString()

        binding.seekBarAlpha.progress = (alpha * 100).toInt()
        binding.checkboxAboveScreen.isChecked = onTop
        binding.checkboxKeepRatio.isChecked = keepAspectRatio

        binding.seekBarAlpha.isVisible = isScreen
        binding.checkboxKeepRatio.isVisible = isScreen
        binding.checkboxAboveScreen.isVisible = isScreen

        currentWidthScale = widthScale
        currentHeightScale = heightScale
        selectedViewMinSize = minSize

        if (!areScalingControlsShown) {
            if (animate) {
                binding.layoutScalingContainer.isVisible = true
                binding.layoutScalingContainer.post {
                    binding.layoutScalingContainer
                        .animate()
                        .y(
                            binding.layoutScalingContainer.bottom.toFloat() -
                                binding.layoutScalingContainer.height.toFloat()
                        )
                        .setDuration(CONTROLS_SLIDE_ANIMATION_DURATION_MS)
                        .start()
                }
            } else {
                binding.layoutScalingContainer.isVisible = true
                binding.layoutScalingContainer.y =
                    binding.layoutScalingContainer.bottom.toFloat() -
                        binding.layoutScalingContainer.height.toFloat()
            }

            areScalingControlsShown = true
        }
    }

    private fun hideScalingControls(animate: Boolean = true) {
        if (!areScalingControlsShown) {
            return
        }

        binding.layoutScalingContainer.clearAnimation()

        if (animate) {
            binding.layoutScalingContainer.post {
                binding.layoutScalingContainer
                    .animate()
                    .y(binding.layoutScalingContainer.bottom.toFloat())
                    .setDuration(CONTROLS_SLIDE_ANIMATION_DURATION_MS)
                    .withEndAction {
                        binding.layoutScalingContainer.isInvisible = true
                    }
                    .start()
            }
        } else {
            binding.layoutScalingContainer.y = binding.layoutScalingContainer.bottom.toFloat()
            binding.layoutScalingContainer.isInvisible = true
        }

        areScalingControlsShown = false
        binding.seekBarAlpha.isVisible = false
        binding.checkboxKeepRatio.isVisible = false
        binding.checkboxAboveScreen.isVisible = false
    }

    private fun openButtonsMenu() {
        hideBottomControls()
        val instantiatedComponents = binding.viewLayoutEditor.getInstantiatedComponents()
        val availableComponents = if (isExternalLayout) {
            listOf(LayoutComponent.TOP_SCREEN, LayoutComponent.BOTTOM_SCREEN)
        } else {
            LayoutComponent.entries
        }
        val componentsToShow = availableComponents.filterNot { instantiatedComponents.contains(it) }

        val dialogBuilder = AlertDialog.Builder(this)
                .setTitle(R.string.choose_component)
                .setNegativeButton(R.string.cancel) { dialog, _ ->
                    dialog.cancel()
                }

        if (componentsToShow.isNotEmpty()) {
            dialogBuilder.setItems(componentsToShow.map { getString(getLayoutComponentName(it)) }.toTypedArray()) { _, which ->
                val component = componentsToShow[which]
                binding.viewLayoutEditor.addLayoutComponent(component)
            }
        } else {
            dialogBuilder.setMessage(R.string.no_more_components)
        }

        dialogBuilder.show()
    }

    private fun openMenu() {
        storeLayoutChanges()
        val values = MenuOption.entries
        val options = Array(values.size) { i -> getString(values[i].stringRes) }

        AlertDialog.Builder(this)
                .setTitle(R.string.menu)
                .setItems(options) { _, which -> onMenuOptionSelected(values[which]) }
                .setNegativeButton(R.string.cancel, null)
                .show()
    }

    private fun onMenuOptionSelected(option: MenuOption) {
        when (option) {
            MenuOption.PROPERTIES -> openPropertiesDialog()
            MenuOption.BACKGROUNDS -> openBackgroundsConfigDialog()
            MenuOption.REVERT -> viewModel.revertLayoutChanges()
            MenuOption.RESET -> viewModel.resetLayout()
            MenuOption.SAVE_AND_EXIT -> {
                if (viewModel.currentLayoutHasName()) {
                    saveLayoutAndExit()
                } else {
                    showLayoutNameInputDialog()
                }
            }
            MenuOption.EXIT_WITHOUT_SAVING -> finish()
        }
    }

    private fun openPropertiesDialog() {
        storeLayoutChanges()
        val layoutConfiguration = viewModel.getCurrentLayoutConfiguration() ?: return
        LayoutPropertiesDialog.newInstance(layoutConfiguration).show(supportFragmentManager, null)
    }

    private fun openBackgroundsConfigDialog() {
        storeLayoutChanges()
        LayoutBackgroundDialog.newInstance().show(supportFragmentManager, null)
    }

    private fun showLayoutNameInputDialog() {
        TextInputDialog.Builder()
            .setTitle(getString(R.string.layout_name))
            .setText(getString(R.string.custom_layout_default_name))
            .setOnConfirmListener {
                viewModel.setCurrentLayoutName(it)
                saveLayoutAndExit()
            }
            .build()
            .show(supportFragmentManager, null)
    }

    private fun saveLayoutAndExit() {
        storeLayoutChanges()
        viewModel.saveCurrentLayout()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        picasso.cancelRequest(binding.imageBackground)
    }
}