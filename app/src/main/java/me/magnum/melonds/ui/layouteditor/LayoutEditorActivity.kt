package me.magnum.melonds.ui.layouteditor

import android.content.res.Configuration
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ActivityLayoutConfigBinding
import me.magnum.melonds.databinding.DialogLayoutNameInputBinding
import me.magnum.melonds.domain.model.*
import me.magnum.melonds.impl.ScreenUnitsConverter
import me.magnum.melonds.utils.getLayoutComponentName
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

@AndroidEntryPoint
class LayoutEditorActivity : AppCompatActivity() {
    companion object {
        const val KEY_LAYOUT_ID = "layout_id"
    }

    enum class Anchor {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    enum class MenuOption(@StringRes val stringRes: Int) {
        REVERT(R.string.revert_changes),
        RESET(R.string.reset_default),
        SAVE_AND_EXIT(R.string.save_and_exit),
        EXIT_WITHOUT_SAVING(R.string.exit_without_saving)
    }

    @Inject
    lateinit var screenUnitsConverter: ScreenUnitsConverter
    private val viewModel: LayoutEditorViewModel by viewModels()
    private val layoutComponentViewBuilderFactory = LayoutComponentViewBuilderFactory()
    private lateinit var binding: ActivityLayoutConfigBinding
    private var areBottomControlsShown = true
    private var areScalingControlsShown = true
    private val defaultComponentWidth by lazy { screenUnitsConverter.dpToPixels(100f).toInt() }
    private val minComponentSize by lazy { screenUnitsConverter.dpToPixels(30f).toInt() }

    private val views = mutableMapOf<LayoutComponent, LayoutComponentView>()
    private var selectedView: LayoutComponentView? = null
    private var selectedViewAnchor = Anchor.TOP_LEFT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLayoutConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonAddButton.setOnClickListener {
            openButtonsMenu()
        }
        binding.buttonMenu.setOnClickListener {
            openMenu()
        }
        binding.buttonDeleteButton.setOnClickListener {
            deleteSelectedView()
        }

        binding.viewLayout.setOnClickListener {
            deselectCurrentView()
            if (areBottomControlsShown)
                hideBottomControls()
            else
                showBottomControls()
        }
        binding.seekBarScaling.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                scaleSelectedView(progress + minComponentSize)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })

        setupFullscreen()
        prepareForLayoutInstantiation()
        hideScalingControls(false)
    }

    override fun onBackPressed() {
        openMenu()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus)
            setupFullscreen()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        storeLayoutChanges()
    }

    private fun storeLayoutChanges() {
        val orientation = if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) LayoutEditorViewModel.LayoutOrientation.PORTRAIT else LayoutEditorViewModel.LayoutOrientation.LANDSCAPE
        viewModel.saveLayoutToCurrentConfiguration(buildCurrentLayout(), orientation)
    }

    private fun buildCurrentLayout(): UILayout {
        return UILayout(views.values.map { PositionedLayoutComponent(it.getRect(), it.component) })
    }

    private fun setupFullscreen() {
        window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    private fun prepareForLayoutInstantiation() {
        val root = findViewById<View>(android.R.id.content)
        root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                root.viewTreeObserver.removeOnGlobalLayoutListener(this)

                val currentLayoutConfiguration = viewModel.getCurrentLayoutConfiguration()
                if (currentLayoutConfiguration == null)
                    instantiateDefaultConfiguration()
                else
                    instantiateLayout(currentLayoutConfiguration)
            }
        })
    }

    private fun instantiateDefaultConfiguration() {
        val decorView = window.decorView
        val defaultLayout = if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            viewModel.getDefaultLayoutConfiguration(decorView.width, decorView.height)
        } else {
            viewModel.getDefaultLayoutConfiguration(decorView.height, decorView.width)
        }

        viewModel.setCurrentLayoutConfiguration(defaultLayout)
        instantiateLayout(defaultLayout)
    }

    private fun instantiateLayout(layoutConfiguration: LayoutConfiguration) {
        views.clear()
        binding.viewLayout.removeAllViews()

        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            loadLayout(layoutConfiguration.portraitLayout)
        } else {
            loadLayout(layoutConfiguration.landscapeLayout)
        }
    }

    private fun loadLayout(layout: UILayout) {
        layout.components.forEach {
            views[it.component] = addPositionedLayoutComponent(it)
        }
    }

    private fun addPositionedLayoutComponent(layoutComponent: PositionedLayoutComponent): LayoutComponentView {
        val viewBuilder = layoutComponentViewBuilderFactory.getLayoutComponentViewBuilder(layoutComponent.component)
        val view = viewBuilder.build(this).apply {
            alpha = 0.5f
        }

        val viewParams = FrameLayout.LayoutParams(layoutComponent.rect.width, layoutComponent.rect.height).apply {
            leftMargin = layoutComponent.rect.x
            topMargin = layoutComponent.rect.y
        }

        val viewLayoutComponent = LayoutComponentView(view, viewBuilder.getAspectRatio(), layoutComponent.component)
        setupDragHandler(viewLayoutComponent)
        if (layoutComponent.isScreen()) {
            // Screens should be below other views
            binding.viewLayout.addView(view, 0, viewParams)
        } else {
            binding.viewLayout.addView(view, viewParams)
        }

        return viewLayoutComponent
    }

    private fun setupDragHandler(layoutComponentView: LayoutComponentView) {
        layoutComponentView.view.setOnTouchListener(object : View.OnTouchListener {
            private var dragging = false

            private var downOffsetX = -1f
            private var downOffsetY = -1f

            override fun onTouch(view: View?, motionEvent: MotionEvent?): Boolean {
                if (view == null)
                    return false

                if (selectedView != null) {
                    deselectCurrentView()
                }

                hideBottomControls()
                return when (motionEvent?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downOffsetX = motionEvent.x
                        downOffsetY = motionEvent.y
                        view.alpha = 1f
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!dragging) {
                            val distance = sqrt((motionEvent.x - downOffsetX).pow(2f) + (motionEvent.y - downOffsetY).pow(2f))
                            if (distance >= 25) {
                                dragging = true
                            }
                        } else {
                            dragView(view, motionEvent.x - downOffsetX, motionEvent.y - downOffsetY)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!dragging) {
                            handleViewSelected(layoutComponentView)
                        } else {
                            view.alpha = 0.5f
                            dragging = false
                        }
                        true
                    }
                    else -> false
                }
            }
        })
    }

    private fun deselectCurrentView() {
        selectedView?.let {
            it.view.alpha = 0.5f
        }
        hideScalingControls()
        selectedView = null
    }

    private fun dragView(view: View, offsetX: Float, offsetY: Float) {
        val rootView = window.decorView.rootView.findViewById<FrameLayout>(R.id.view_layout)
        val finalX = min(max(view.x + offsetX, 0f), rootView.width - view.width.toFloat())
        val finalY = min(max(view.y + offsetY, 0f), rootView.height - view.height.toFloat())
        val newParams = FrameLayout.LayoutParams(view.width, view.height).apply {
            leftMargin = finalX.toInt()
            topMargin = finalY.toInt()
        }
        view.layoutParams = newParams
    }

    private fun scaleSelectedView(newDimension: Int) {
        val currentlySelectedView = selectedView ?: return

        val rootView = window.decorView.rootView.findViewById<FrameLayout>(R.id.view_layout)
        val screenAspectRatio = rootView.width / rootView.height.toFloat()
        val selectedViewAspectRatio = currentlySelectedView.aspectRatio
        val newViewWidth: Int
        val newViewHeight: Int

        if (screenAspectRatio > selectedViewAspectRatio) {
            newViewWidth = (newDimension * selectedViewAspectRatio).toInt()
            newViewHeight = newDimension
        } else {
            newViewWidth = newDimension
            newViewHeight = (newDimension / selectedViewAspectRatio).toInt()
        }

        val viewPosition = currentlySelectedView.getPosition()
        var viewX: Int
        var viewY: Int

        if (selectedViewAnchor == Anchor.TOP_LEFT) {
            viewX = viewPosition.x
            viewY = viewPosition.y
            if (viewX + newViewWidth > rootView.width) {
                viewX = rootView.width - newViewWidth
            }
            if (viewY + newViewHeight > rootView.height) {
                viewY = rootView.height - newViewHeight
            }
        } else if (selectedViewAnchor == Anchor.TOP_RIGHT) {
            viewX = viewPosition.x + currentlySelectedView.getWidth() - newViewWidth
            viewY = viewPosition.y
            if (viewX < 0) {
                viewX = 0
            }
            if (viewY + newViewHeight > rootView.height) {
                viewY = rootView.height - newViewHeight
            }
        } else if (selectedViewAnchor == Anchor.BOTTOM_LEFT) {
            viewX = viewPosition.x
            viewY = viewPosition.y + currentlySelectedView.getHeight() - newViewHeight
            if (viewX + newViewWidth > rootView.width) {
                viewX = rootView.width - newViewWidth
            }
            if (viewY < 0) {
                viewY = 0
            }
        } else {
            viewX = viewPosition.x + currentlySelectedView.getWidth() - newViewWidth
            viewY = viewPosition.y + currentlySelectedView.getHeight() - newViewHeight
            if (viewX < 0) {
                viewX = 0
            }
            if (viewY < 0) {
                viewY = 0
            }
        }
        currentlySelectedView.setPositionAndSize(Point(viewX, viewY), newViewWidth, newViewHeight)
    }

    private fun handleViewSelected(view: LayoutComponentView) {
        val rootView = window.decorView.rootView.findViewById<FrameLayout>(R.id.view_layout)
        val anchorDistances = mutableMapOf<Anchor, Double>()
        anchorDistances[Anchor.TOP_LEFT] = view.getPosition().x.toDouble().pow(2) + view.getPosition().y.toDouble().pow(2)
        anchorDistances[Anchor.TOP_RIGHT] = (rootView.width - (view.getPosition().x + view.getWidth())).toDouble().pow(2) + view.getPosition().y.toDouble().pow(2)
        anchorDistances[Anchor.BOTTOM_LEFT] = view.getPosition().x.toDouble().pow(2) + (rootView.height - (view.getPosition().y + view.getHeight())).toDouble().pow(2)
        anchorDistances[Anchor.BOTTOM_RIGHT] = (rootView.width - (view.getPosition().x + view.getWidth())).toDouble().pow(2) + (rootView.height - (view.getPosition().y + view.getHeight())).toDouble().pow(2)

        var anchor = Anchor.TOP_LEFT
        var minDistance = Double.MAX_VALUE
        anchorDistances.keys.forEach {
            if (anchorDistances[it]!! < minDistance) {
                minDistance = anchorDistances[it]!!
                anchor = it
            }
        }

        selectedViewAnchor = anchor
        selectedView = view
        showScalingControls()
    }

    private fun showBottomControls(animate: Boolean = true) {
        if (areBottomControlsShown)
            return

        /*binding.layoutControls.isClickable = true
        if (animate) {
            val animation = TranslateAnimation(0f, 0f, binding.layoutControls.height.toFloat(), 0f).apply {
                duration = 100
                fillAfter = true
            }
            binding.layoutControls.startAnimation(animation)
        } else {
            binding.layoutControls.translationY = 0f
        }*/
        binding.layoutControls.isVisible = true
        areBottomControlsShown = true
    }

    private fun hideBottomControls(animate: Boolean = true) {
        if (!areBottomControlsShown)
            return

       /* binding.layoutControls.isClickable = false
        if (animate) {
            val animation = TranslateAnimation(0f, 0f, binding.layoutControls.translationY, binding.layoutControls.height.toFloat()).apply {
                duration = 100
                fillAfter = true
            }
            binding.layoutControls.startAnimation(animation)
        } else {
            binding.layoutControls.translationY = binding.layoutControls.height.toFloat()
        }*/
        binding.layoutControls.isGone = true
        areBottomControlsShown = false
    }

    private fun showScalingControls(animate: Boolean = true) {
        val currentlySelectedView = selectedView
        if (areScalingControlsShown || currentlySelectedView == null)
            return

        val rootView = window.decorView.rootView.findViewById<FrameLayout>(R.id.view_layout)
        val screenAspectRatio = rootView.width / rootView.height.toFloat()
        val selectedViewAspectRatio = currentlySelectedView.aspectRatio
        val currentConstrainedDimension: Int
        val maxDimension: Int

        if (screenAspectRatio > selectedViewAspectRatio) {
            maxDimension = rootView.height
            currentConstrainedDimension = currentlySelectedView.getHeight()
        } else {
            maxDimension = rootView.width
            currentConstrainedDimension = currentlySelectedView.getWidth()
        }

        binding.seekBarScaling.max = maxDimension - minComponentSize
        binding.seekBarScaling.progress = currentConstrainedDimension - minComponentSize

        /*binding.layoutScaling.isClickable = true
        if (animate) {
            val animation = TranslateAnimation(0f, 0f, binding.layoutScaling.height.toFloat(), 0f).apply {
                duration = 100
                fillAfter = true
            }
            binding.layoutScaling.startAnimation(animation)
        } else {
            binding.layoutScaling.translationY = 0f
        }*/
        binding.layoutScaling.isVisible = true
        areScalingControlsShown = true
    }

    private fun hideScalingControls(animate: Boolean = true) {
        if (!areScalingControlsShown)
            return

        /*binding.layoutScaling.isClickable = false
        if (animate) {
            val animation = TranslateAnimation(0f, 0f, binding.layoutScaling.translationY, binding.layoutScaling.height.toFloat()).apply {
                duration = 100
                fillAfter = true
            }
            binding.layoutScaling.startAnimation(animation)
        } else {
            binding.layoutScaling.translationY = binding.layoutScaling.height.toFloat()
        }*/
        binding.layoutScaling.isVisible = false
        areScalingControlsShown = false
    }

    private fun openButtonsMenu() {
        hideBottomControls()
        val inputsToShow = LayoutComponent.values().filterNot { views.containsKey(it) }

        val dialogBuilder = AlertDialog.Builder(this)
                .setTitle(R.string.choose_component)
                .setNegativeButton(R.string.cancel) { dialog, _ ->
                    dialog.cancel()
                }

        if (inputsToShow.isNotEmpty()) {
            dialogBuilder.setItems(inputsToShow.map { getString(getLayoutComponentName(it)) }.toTypedArray()) { _, which ->
                val input = inputsToShow[which]
                val componentBuilder = layoutComponentViewBuilderFactory.getLayoutComponentViewBuilder(input)
                val componentHeight = defaultComponentWidth / componentBuilder.getAspectRatio()

                val newView = addPositionedLayoutComponent(PositionedLayoutComponent(Rect(0, 0, defaultComponentWidth, componentHeight.toInt()), input))
                views[input] = newView
            }
        } else {
            dialogBuilder.setMessage(R.string.no_more_components)
        }

        dialogBuilder.show()
    }

    private fun openMenu() {
        val values = MenuOption.values()
        val options = Array(values.size) { i -> getString(values[i].stringRes) }

        AlertDialog.Builder(this)
                .setTitle(R.string.menu)
                .setItems(options) { _, which -> onMenuOptionSelected(values[which]) }
                .setNegativeButton(R.string.cancel, null)
                .show()
    }

    private fun onMenuOptionSelected(option: MenuOption) {
        when (option) {
            MenuOption.REVERT -> revertLayoutConfiguration()
            MenuOption.RESET -> instantiateDefaultConfiguration()
            MenuOption.SAVE_AND_EXIT -> {
                if (viewModel.isCurrentLayoutNew()) {
                    showLayoutNameInputDialog()
                } else {
                    saveLayoutAndExit()
                }
            }
            MenuOption.EXIT_WITHOUT_SAVING -> finish()
        }
    }

    private fun showLayoutNameInputDialog() {
        val binding = DialogLayoutNameInputBinding.inflate(layoutInflater)

        AlertDialog.Builder(this)
                .setTitle(R.string.layout_name)
                .setView(binding.root)
                .setPositiveButton(R.string.ok) { _, _ ->
                    val layoutName = binding.editTextLayoutName.text.toString()
                    viewModel.setCurrentLayoutName(layoutName)
                    saveLayoutAndExit()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()

        binding.editTextLayoutName.requestFocus()
        /*val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)*/
    }

    private fun saveLayoutAndExit() {
        storeLayoutChanges()
        viewModel.saveCurrentLayout()
        finish()
    }

    private fun deleteSelectedView() {
        val currentlySelectedView = selectedView ?: return
        deselectCurrentView()
        binding.viewLayout.removeView(currentlySelectedView.view)
        views.remove(currentlySelectedView.component)
    }

    private fun revertLayoutConfiguration() {
        viewModel.getInitialLayoutConfiguration()?.let {
            viewModel.setCurrentLayoutConfiguration(it)
            instantiateLayout(it)
        }
    }
}