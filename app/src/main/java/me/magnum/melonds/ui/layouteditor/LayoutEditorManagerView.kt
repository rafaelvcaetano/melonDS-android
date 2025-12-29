package me.magnum.melonds.ui.layouteditor

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.get
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ActivityLayoutEditorBinding
import me.magnum.melonds.domain.model.RuntimeBackground
import me.magnum.melonds.domain.model.layout.LayoutComponent
import me.magnum.melonds.extensions.setBackgroundMode
import me.magnum.melonds.ui.common.component.dialog.TextInputDialog
import me.magnum.melonds.ui.common.component.dialog.TextInputDialogState
import me.magnum.melonds.ui.layouteditor.LayoutEditorActivity.MenuOption
import me.magnum.melonds.ui.layouteditor.model.LayoutTarget
import me.magnum.melonds.ui.layouteditor.model.ScreenEditorState
import me.magnum.melonds.ui.layouteditor.ui.LayoutBackgroundDialog
import me.magnum.melonds.ui.layouteditor.ui.LayoutPropertiesDialog
import me.magnum.melonds.ui.theme.MelonTheme
import me.magnum.melonds.utils.getLayoutComponentName
import java.util.UUID
import kotlin.math.min

private const val CONTROLS_SLIDE_ANIMATION_DURATION_MS = 100L

class LayoutEditorManagerView(
    private val layoutTarget: LayoutTarget,
    private val picasso: Picasso,
    initialEditorState: ScreenEditorState? = null,
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    interface LayoutEditorManagerListener {
        fun openBackgroundPicker(layoutTarget: LayoutTarget, selectedBackgroundId: UUID?)
        fun onStoreLayoutChanges()
        fun onSaveLayoutAndExit()
        fun onExit()
    }

    private val binding: ActivityLayoutEditorBinding
    private val viewModel: LayoutEditorViewModel by lazy {
        val owner = findViewTreeViewModelStoreOwner() ?: error("No view-model store owner found")
        ViewModelProvider(owner).get<LayoutEditorViewModel>()
    }

    var listener: LayoutEditorManagerListener? = null
    private var areBottomControlsShown = true
    private var areScalingControlsShown = true
    private var selectedViewMinSize = 0
    private var currentWidthScale = 0f
    private var currentHeightScale = 0f
    private var selectedViewIsScreen = false
    private var selectedScreenComponent: LayoutComponent? = null
    private var selectedAspectRatio = ScreenAspectRatio.RATIO_4_3
    private var topAspectRatio = ScreenAspectRatio.RATIO_4_3
    private var bottomAspectRatio = ScreenAspectRatio.RATIO_4_3
    private var updatingAspectSpinner = false

    private var showLayoutPropertiesDialog by mutableStateOf(initialEditorState?.isPropertiesDialogShown ?: false)
    private var showBackgroundPropertiesDialog by mutableStateOf(initialEditorState?.isBackgroundPropertiesDialogShown ?: false)
    private val nameInputDialogState = TextInputDialogState()

    private val dsRatio = 256f / 192f
    private val widescreenRatio = 16f / 9f

    private enum class ScreenAspectRatio {
        RATIO_4_3,
        RATIO_16_9,
        UNRESTRICTED
    }

    val layoutEditorView get() = binding.viewLayoutEditor
    val imageBackground get() = binding.imageBackground

    init {
        val layoutInflater = LayoutInflater.from(context)
        isFocusable = true
        isFocusableInTouchMode = true
        binding = ActivityLayoutEditorBinding.inflate(layoutInflater)
        val composeView = ComposeView(context).apply {
            setContent {
                MelonTheme {
                    if (showLayoutPropertiesDialog) {
                        val layoutConfiguration by viewModel.currentLayoutConfiguration.collectAsStateWithLifecycle()
                        val currentConfiguration = layoutConfiguration

                        if (currentConfiguration != null) {
                            LayoutPropertiesDialog(
                                layoutConfiguration = currentConfiguration,
                                onDismiss = { showLayoutPropertiesDialog = false },
                                onSave = { name, orientation, useCustomOpacity, opacity ->
                                    viewModel.savePropertiesToCurrentConfiguration(name, orientation, useCustomOpacity, opacity)
                                    showLayoutPropertiesDialog = false
                                }
                            )
                        }
                    }

                    if (showBackgroundPropertiesDialog) {
                        val backgroundProperties by when (layoutTarget) {
                            LayoutTarget.MAIN_SCREEN -> viewModel.mainScreenBackgroundProperties.collectAsStateWithLifecycle()
                            LayoutTarget.SECONDARY_SCREEN -> viewModel.secondaryScreenBackgroundProperties.collectAsStateWithLifecycle()
                        }
                        val currentBackgroundProperties = backgroundProperties

                        if (currentBackgroundProperties != null) {
                            LayoutBackgroundDialog(
                                backgroundId = currentBackgroundProperties.backgroundId,
                                backgroundMode = currentBackgroundProperties.backgroundMode,
                                loadBackgroundName = { backgroundId -> viewModel.getBackgroundName(backgroundId) },
                                onOpenBackgroundPicker = { listener?.openBackgroundPicker(layoutTarget, currentBackgroundProperties.backgroundId) },
                                onBackgroundModeUpdate = { viewModel.setBackgroundPropertiesBackgroundMode(layoutTarget, it) },
                                onDismiss = {
                                    viewModel.resetBackgroundProperties(layoutTarget)
                                    showBackgroundPropertiesDialog = false
                                },
                                onSave = {
                                    viewModel.saveBackgroundToCurrentConfiguration(layoutTarget)
                                    showBackgroundPropertiesDialog = false
                                }
                            )
                        }
                    }

                    TextInputDialog(
                        title = stringResource(R.string.layout_name),
                        dialogState = nameInputDialogState,
                        allowEmpty = false,
                    )
                }
            }
        }
        addView(binding.root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(composeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        binding.buttonAddButton.setOnClickListener {
            openButtonsMenu()
        }
        binding.buttonMenu.setOnClickListener {
            openMenu()
        }
        binding.buttonDeleteButton.setOnClickListener {
            binding.viewLayoutEditor.deleteSelectedView()
        }
        binding.buttonCenterHorizontal.setOnClickListener {
            binding.viewLayoutEditor.centerSelectedViewHorizontally()
        }
        binding.buttonCenterVertical.setOnClickListener {
            binding.viewLayoutEditor.centerSelectedViewVertically()
        }

        binding.viewLayoutEditor.setLayoutComponentViewBuilderFactory(EditorLayoutComponentViewBuilderFactory())
        binding.viewLayoutEditor.setOnClickListener {
            if (areBottomControlsShown)
                hideBottomControls()
            else
                showBottomControls()
        }
        binding.viewLayoutEditor.setOnViewSelectedListener { view, widthScale, heightScale, maxWidth, maxHeight, minSize ->
            hideBottomControls()
            // Force the scaling controls to restart so the new view always
            // receives fresh listeners even if they were already visible.
            hideScalingControls(false)
            selectedViewIsScreen = view.component.isScreen()
            selectedScreenComponent = view.component
            selectedAspectRatio = when (view.component) {
                LayoutComponent.TOP_SCREEN -> topAspectRatio
                LayoutComponent.BOTTOM_SCREEN -> bottomAspectRatio
                else -> ScreenAspectRatio.UNRESTRICTED
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
            hideScalingControls()
        }
        binding.seekBarWidth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                var adjustedProgress = progress
                var widthScale = adjustedProgress / binding.seekBarWidth.max.toFloat()
                var width = (binding.seekBarWidth.max - selectedViewMinSize) * widthScale + selectedViewMinSize
                if (selectedViewIsScreen && fromUser) {
                    when (selectedAspectRatio) {
                        ScreenAspectRatio.RATIO_4_3 -> {
                            val maxWidth = min(binding.seekBarWidth.max.toFloat(), binding.seekBarHeight.max * dsRatio)
                            if (width > maxWidth) {
                                width = maxWidth
                                widthScale = ((width - selectedViewMinSize) / (binding.seekBarWidth.max - selectedViewMinSize).toFloat()).coerceIn(0f, 1f)
                                adjustedProgress = (widthScale * binding.seekBarWidth.max).toInt()
                                binding.seekBarWidth.progress = adjustedProgress
                            }
                            val height = width / dsRatio
                            currentHeightScale = ((height - selectedViewMinSize) / (binding.seekBarHeight.max - selectedViewMinSize).toFloat()).coerceIn(0f, 1f)
                            binding.seekBarHeight.progress = (currentHeightScale * binding.seekBarHeight.max).toInt()
                            binding.textHeight.text = ((binding.seekBarHeight.max - selectedViewMinSize) * currentHeightScale + selectedViewMinSize).toInt().toString()
                        }
                        ScreenAspectRatio.RATIO_16_9 -> {
                            val maxWidth = min(binding.seekBarWidth.max.toFloat(), binding.seekBarHeight.max * widescreenRatio)
                            if (width > maxWidth) {
                                width = maxWidth
                                widthScale = ((width - selectedViewMinSize) / (binding.seekBarWidth.max - selectedViewMinSize).toFloat()).coerceIn(0f, 1f)
                                adjustedProgress = (widthScale * binding.seekBarWidth.max).toInt()
                                binding.seekBarWidth.progress = adjustedProgress
                            }
                            val height = width / widescreenRatio
                            currentHeightScale = ((height - selectedViewMinSize) / (binding.seekBarHeight.max - selectedViewMinSize).toFloat()).coerceIn(0f, 1f)
                            binding.seekBarHeight.progress = (currentHeightScale * binding.seekBarHeight.max).toInt()
                            binding.textHeight.text = ((binding.seekBarHeight.max - selectedViewMinSize) * currentHeightScale + selectedViewMinSize).toInt().toString()
                        }
                        ScreenAspectRatio.UNRESTRICTED -> { }
                    }
                }
                currentWidthScale = widthScale
                binding.textWidth.text = ((binding.seekBarWidth.max - selectedViewMinSize) * currentWidthScale + selectedViewMinSize).toInt().toString()
                binding.viewLayoutEditor.scaleSelectedView(currentWidthScale, currentHeightScale)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })

        binding.seekBarHeight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                var adjustedProgress = progress
                var heightScale = adjustedProgress / binding.seekBarHeight.max.toFloat()
                var height = (binding.seekBarHeight.max - selectedViewMinSize) * heightScale + selectedViewMinSize
                if (selectedViewIsScreen && fromUser) {
                    when (selectedAspectRatio) {
                        ScreenAspectRatio.RATIO_4_3 -> {
                            val maxHeight = min(binding.seekBarHeight.max.toFloat(), binding.seekBarWidth.max / dsRatio)
                            if (height > maxHeight) {
                                height = maxHeight
                                heightScale = ((height - selectedViewMinSize) / (binding.seekBarHeight.max - selectedViewMinSize).toFloat()).coerceIn(0f, 1f)
                                adjustedProgress = (heightScale * binding.seekBarHeight.max).toInt()
                                binding.seekBarHeight.progress = adjustedProgress
                            }
                            val width = height * dsRatio
                            currentWidthScale = ((width - selectedViewMinSize) / (binding.seekBarWidth.max - selectedViewMinSize).toFloat()).coerceIn(0f, 1f)
                            binding.seekBarWidth.progress = (currentWidthScale * binding.seekBarWidth.max).toInt()
                            binding.textWidth.text = ((binding.seekBarWidth.max - selectedViewMinSize) * currentWidthScale + selectedViewMinSize).toInt().toString()
                        }
                        ScreenAspectRatio.RATIO_16_9 -> {
                            val maxHeight = min(binding.seekBarHeight.max.toFloat(), binding.seekBarWidth.max / widescreenRatio)
                            if (height > maxHeight) {
                                height = maxHeight
                                heightScale = ((height - selectedViewMinSize) / (binding.seekBarHeight.max - selectedViewMinSize).toFloat()).coerceIn(0f, 1f)
                                adjustedProgress = (heightScale * binding.seekBarHeight.max).toInt()
                                binding.seekBarHeight.progress = adjustedProgress
                            }
                            val width = height * widescreenRatio
                            currentWidthScale = ((width - selectedViewMinSize) / (binding.seekBarWidth.max - selectedViewMinSize).toFloat()).coerceIn(0f, 1f)
                            binding.seekBarWidth.progress = (currentWidthScale * binding.seekBarWidth.max).toInt()
                            binding.textWidth.text = ((binding.seekBarWidth.max - selectedViewMinSize) * currentWidthScale + selectedViewMinSize).toInt().toString()
                        }
                        ScreenAspectRatio.UNRESTRICTED -> { }
                    }
                }
                currentHeightScale = heightScale
                binding.textHeight.text = ((binding.seekBarHeight.max - selectedViewMinSize) * currentHeightScale + selectedViewMinSize).toInt().toString()
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

        val aspectOptions = listOf(
            resources.getString(R.string.aspect_ratio_4_3),
            resources.getString(R.string.aspect_ratio_16_9),
            resources.getString(R.string.aspect_ratio_unrestricted),
        )
        val aspectAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, aspectOptions)
        aspectAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerAspectRatio.adapter = aspectAdapter
        binding.spinnerAspectRatio.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (!selectedViewIsScreen || updatingAspectSpinner) return
                selectedAspectRatio = ScreenAspectRatio.values()[position]
                when (selectedScreenComponent) {
                    LayoutComponent.TOP_SCREEN -> topAspectRatio = selectedAspectRatio
                    LayoutComponent.BOTTOM_SCREEN -> bottomAspectRatio = selectedAspectRatio
                    else -> {}
                }
                when (selectedAspectRatio) {
                    ScreenAspectRatio.RATIO_4_3 -> {
                        var width = (binding.seekBarWidth.max - selectedViewMinSize) * currentWidthScale + selectedViewMinSize
                        val maxWidth = min(binding.seekBarWidth.max.toFloat(), binding.seekBarHeight.max * dsRatio)
                        if (width > maxWidth) {
                            width = maxWidth
                            currentWidthScale = ((width - selectedViewMinSize) / (binding.seekBarWidth.max - selectedViewMinSize).toFloat()).coerceIn(0f, 1f)
                            binding.seekBarWidth.progress = (currentWidthScale * binding.seekBarWidth.max).toInt()
                            binding.textWidth.text = ((binding.seekBarWidth.max - selectedViewMinSize) * currentWidthScale + selectedViewMinSize).toInt().toString()
                        }
                        val height = width / dsRatio
                        currentHeightScale = ((height - selectedViewMinSize) / (binding.seekBarHeight.max - selectedViewMinSize).toFloat()).coerceIn(0f, 1f)
                        binding.seekBarHeight.progress = (currentHeightScale * binding.seekBarHeight.max).toInt()
                        binding.textHeight.text = ((binding.seekBarHeight.max - selectedViewMinSize) * currentHeightScale + selectedViewMinSize).toInt().toString()
                    }
                    ScreenAspectRatio.RATIO_16_9 -> {
                        var width = (binding.seekBarWidth.max - selectedViewMinSize) * currentWidthScale + selectedViewMinSize
                        val maxWidth = min(binding.seekBarWidth.max.toFloat(), binding.seekBarHeight.max * widescreenRatio)
                        if (width > maxWidth) {
                            width = maxWidth
                            currentWidthScale = ((width - selectedViewMinSize) / (binding.seekBarWidth.max - selectedViewMinSize).toFloat()).coerceIn(0f, 1f)
                            binding.seekBarWidth.progress = (currentWidthScale * binding.seekBarWidth.max).toInt()
                            binding.textWidth.text = ((binding.seekBarWidth.max - selectedViewMinSize) * currentWidthScale + selectedViewMinSize).toInt().toString()
                        }
                        val height = width / widescreenRatio
                        currentHeightScale = ((height - selectedViewMinSize) / (binding.seekBarHeight.max - selectedViewMinSize).toFloat()).coerceIn(0f, 1f)
                        binding.seekBarHeight.progress = (currentHeightScale * binding.seekBarHeight.max).toInt()
                        binding.textHeight.text = ((binding.seekBarHeight.max - selectedViewMinSize) * currentHeightScale + selectedViewMinSize).toInt().toString()
                    }
                    ScreenAspectRatio.UNRESTRICTED -> { }
                }
                binding.viewLayoutEditor.scaleSelectedView(currentWidthScale, currentHeightScale)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.checkboxAboveScreen.setOnCheckedChangeListener { _, isChecked ->
            binding.viewLayoutEditor.setSelectedScreenOnTop(isChecked)
        }

        hideScalingControls(false)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return if (binding.viewLayoutEditor.handleKeyDown(event)) {
            true
        } else {
            super.onKeyDown(keyCode, event)
        }
    }

    fun saveEditorState(): ScreenEditorState {
        return ScreenEditorState(
            isMenuShown = false,
            isPropertiesDialogShown = showLayoutPropertiesDialog,
            isBackgroundPropertiesDialogShown = showBackgroundPropertiesDialog,
        )
    }

    fun updateBackground(background: RuntimeBackground) {
        picasso.load(background.background?.uri).into(binding.imageBackground, object : Callback {
            override fun onSuccess() {
                binding.imageBackground.setBackgroundMode(background.mode)
            }

            override fun onError(e: java.lang.Exception?) {
                e?.printStackTrace()
                Toast.makeText(context, R.string.layout_background_load_failed, Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun openButtonsMenu() {
        hideBottomControls()
        val instantiatedComponents = binding.viewLayoutEditor.getInstantiatedComponents()
        val componentsToShow = LayoutComponent.entries.filterNot { instantiatedComponents.contains(it) }

        val themedContext = android.view.ContextThemeWrapper(context, R.style.AppTheme)
        val dialogBuilder = AlertDialog.Builder(themedContext)
            .setTitle(R.string.choose_component)
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.cancel()
            }

        if (componentsToShow.isNotEmpty()) {
            dialogBuilder.setItems(componentsToShow.map { resources.getString(getLayoutComponentName(it)) }.toTypedArray()) { _, which ->
                val component = componentsToShow[which]
                binding.viewLayoutEditor.addLayoutComponent(component)
            }
        } else {
            dialogBuilder.setMessage(R.string.no_more_components)
        }

        dialogBuilder.showInCurrentWindow()
    }

    fun openMenu() {
        listener?.onStoreLayoutChanges()
        val values = MenuOption.entries
        val options = Array(values.size) { i -> resources.getString(values[i].stringRes) }

        val themedContext = android.view.ContextThemeWrapper(context, R.style.AppTheme)
        AlertDialog.Builder(themedContext)
            .setTitle(R.string.menu)
            .setItems(options) { _, which -> onMenuOptionSelected(values[which]) }
            .setNegativeButton(R.string.cancel, null)
            .showInCurrentWindow()
    }

    private fun onMenuOptionSelected(option: MenuOption) {
        when (option) {
            MenuOption.PROPERTIES -> openPropertiesDialog()
            MenuOption.BACKGROUNDS -> openBackgroundsConfigDialog()
            MenuOption.REVERT -> viewModel.revertLayoutChanges()
            MenuOption.RESET -> viewModel.resetLayout()
            MenuOption.SAVE_AND_EXIT -> {
                if (viewModel.currentLayoutHasName()) {
                    listener?.onSaveLayoutAndExit()
                } else {
                    showLayoutNameInputDialog()
                }
            }
            MenuOption.EXIT_WITHOUT_SAVING -> listener?.onExit()
        }
    }

    private fun openPropertiesDialog() {
        listener?.onStoreLayoutChanges()
        showLayoutPropertiesDialog = true
    }

    private fun openBackgroundsConfigDialog() {
        listener?.onStoreLayoutChanges()
        showBackgroundPropertiesDialog = true
    }

    private fun showLayoutNameInputDialog() {
        nameInputDialogState.show(
            initialText = resources.getString(R.string.custom_layout_default_name),
            onConfirm = {
                viewModel.setCurrentLayoutName(it)
                listener?.onSaveLayoutAndExit()
            },
        )
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
            binding.layoutControls.animate()
                .y(binding.layoutControls.bottom.toFloat())
                .setDuration(CONTROLS_SLIDE_ANIMATION_DURATION_MS)
                .withEndAction {
                    binding.layoutControls.isGone = true
                }
                .start()
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
        updatingAspectSpinner = true
        binding.spinnerAspectRatio.setSelection(selectedAspectRatio.ordinal, false)
        updatingAspectSpinner = false

        binding.seekBarAlpha.isVisible = isScreen
        binding.layoutAspectRatio.isVisible = isScreen
        binding.checkboxAboveScreen.isVisible = isScreen
        binding.buttonCenterHorizontal.isVisible = isScreen
        binding.buttonCenterVertical.isVisible = isScreen

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
    }

    /**
     * Allows an [AlertDialog] to be shown on a window that is not the main application window. This is required when the layout editor is being shown on a secondary display.
     */
    private fun AlertDialog.Builder.showInCurrentWindow(): AlertDialog {
        val dialog = create()
        dialog.window?.apply {
            setType(WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG)
            attributes.token = this@LayoutEditorManagerView.windowToken
        }
        dialog.show()
        return dialog
    }
}