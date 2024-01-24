package me.magnum.melonds.ui.layouteditor

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.*
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ActivityLayoutEditorBinding
import me.magnum.melonds.domain.model.LayoutComponent
import me.magnum.melonds.domain.model.LayoutConfiguration
import me.magnum.melonds.domain.model.Orientation
import me.magnum.melonds.domain.model.RuntimeBackground
import me.magnum.melonds.extensions.insetsControllerCompat
import me.magnum.melonds.extensions.setBackgroundMode
import me.magnum.melonds.extensions.setLayoutOrientation
import me.magnum.melonds.impl.ScreenUnitsConverter
import me.magnum.melonds.ui.common.TextInputDialog
import me.magnum.melonds.utils.getLayoutComponentName
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class LayoutEditorActivity : AppCompatActivity() {
    companion object {
        const val KEY_LAYOUT_ID = "layout_id"

        private const val CONTROLS_SLIDE_ANIMATION_DURATION_MS = 100L
    }

    enum class MenuOption(@StringRes val stringRes: Int) {
        PROPERTIES(R.string.properties),
        BACKGROUNDS(R.string.backgrounds),
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
    private var currentlySelectedBackgroundId: UUID? = null
    private var areBottomControlsShown = true
    private var areScalingControlsShown = true
    private var selectedViewMinSize = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLayoutEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
        binding.viewLayoutEditor.setOnViewSelectedListener { _, scale, maxSize, minSize ->
            hideBottomControls()
            showScalingControls(scale, maxSize, minSize)
        }
        binding.viewLayoutEditor.setOnViewDeselectedListener {
            hideScalingControls()
        }
        binding.seekBarScaling.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val scale = progress / binding.seekBarScaling.max.toFloat()
                binding.textSize.text = ((binding.seekBarScaling.max - selectedViewMinSize) * scale + selectedViewMinSize).toInt().toString()
                binding.viewLayoutEditor.scaleSelectedView(scale)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })

        val currentOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Orientation.PORTRAIT
        } else {
            Orientation.LANDSCAPE
        }
        viewModel.setCurrentSystemOrientation(currentOrientation)
        viewModel.getBackground().observe(this) {
            updateBackground(it)
        }
        viewModel.onLayoutPropertiesUpdate().observe(this) {
            onLayoutPropertiesUpdated()
        }

        setupFullscreen()
        instantiateLayout()
        hideScalingControls(false)
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        storeLayoutChanges()
    }

    private fun storeLayoutChanges() {
        val layoutConfiguration = binding.viewLayoutEditor.buildCurrentLayout().copy(
                backgroundId = currentlySelectedBackgroundId
        )
        viewModel.saveLayoutToCurrentConfiguration(layoutConfiguration)
    }

    private fun setupFullscreen() {
        window.insetsControllerCompat?.let {
            it.hide(WindowInsetsCompat.Type.navigationBars())
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun instantiateLayout() {
        val currentLayoutConfiguration = viewModel.getCurrentLayoutConfiguration()
        if (currentLayoutConfiguration == null) {
            instantiateDefaultConfiguration()
            currentlySelectedBackgroundId = null
        } else {
            binding.viewLayoutEditor.instantiateLayout(currentLayoutConfiguration)
            setLayoutOrientation(currentLayoutConfiguration.orientation)
        }
    }

    private fun instantiateDefaultConfiguration() {
        val defaultLayout = viewModel.getDefaultLayoutConfiguration()
        viewModel.setCurrentLayoutConfiguration(defaultLayout)
        binding.viewLayoutEditor.instantiateLayout(defaultLayout)
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

    private fun onLayoutPropertiesUpdated() {
        viewModel.getCurrentLayoutConfiguration()?.let {
            val desiredSystemOrientation = when(it.orientation) {
                LayoutConfiguration.LayoutOrientation.FOLLOW_SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                LayoutConfiguration.LayoutOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                LayoutConfiguration.LayoutOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }

            if (desiredSystemOrientation != requestedOrientation) {
                requestedOrientation = desiredSystemOrientation
            }
        }
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

    private fun showScalingControls(currentScale: Float, maxSize: Int, minSize: Int, animate: Boolean = true) {
        binding.seekBarScaling.apply {
            max = maxSize
            progress = (currentScale * maxSize).toInt()
        }

        selectedViewMinSize = minSize

        if (areScalingControlsShown) {
            return
        }

        if (animate) {
            binding.layoutScaling
                .animate()
                .y(binding.layoutScaling.bottom.toFloat() - binding.layoutScaling.height.toFloat())
                .setDuration(CONTROLS_SLIDE_ANIMATION_DURATION_MS)
                .withStartAction {
                    binding.layoutScaling.isVisible = true
                }
                .start()
        } else {
            binding.layoutScaling.isVisible = true
        }

        areScalingControlsShown = true
    }

    private fun hideScalingControls(animate: Boolean = true) {
        if (!areScalingControlsShown) {
            return
        }

        if (animate) {
            binding.layoutScaling
                .animate()
                .y(binding.layoutScaling.bottom.toFloat())
                .setDuration(CONTROLS_SLIDE_ANIMATION_DURATION_MS)
                .withEndAction {
                    binding.layoutScaling.isInvisible = true
                }
                .start()
        } else {
            binding.layoutScaling.isInvisible = true
        }

        areScalingControlsShown = false
    }

    private fun openButtonsMenu() {
        hideBottomControls()
        val instantiatedComponents = binding.viewLayoutEditor.getInstantiatedComponents()
        val componentsToShow = LayoutComponent.entries.filterNot { instantiatedComponents.contains(it) }

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

    private fun openPropertiesDialog() {
        storeLayoutChanges()
        val layoutConfiguration = viewModel.getCurrentLayoutConfiguration() ?: return
        LayoutPropertiesDialog.newInstance(layoutConfiguration).show(supportFragmentManager, null)
    }

    private fun openBackgroundsConfigDialog() {
        storeLayoutChanges()
        val layoutConfiguration = viewModel.getCurrentLayoutConfiguration() ?: return
        LayoutBackgroundsDialog.newInstance(layoutConfiguration).show(supportFragmentManager, null)
    }

    private fun showLayoutNameInputDialog() {
        TextInputDialog.Builder()
                .setTitle(getString(R.string.layout_name))
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

    private fun revertLayoutConfiguration() {
        viewModel.getInitialLayoutConfiguration()?.let {
            viewModel.setCurrentLayoutConfiguration(it)
            binding.viewLayoutEditor.instantiateLayout(it)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        picasso.cancelRequest(binding.imageBackground)
    }
}