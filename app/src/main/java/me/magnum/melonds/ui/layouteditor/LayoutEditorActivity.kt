package me.magnum.melonds.ui.layouteditor

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.widget.SeekBar
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
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
import me.magnum.melonds.impl.ScreenUnitsConverter
import me.magnum.melonds.ui.common.TextInputDialog
import me.magnum.melonds.utils.getLayoutComponentName
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class LayoutEditorActivity : AppCompatActivity() {
    companion object {
        const val KEY_LAYOUT_ID = "layout_id"
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

    override fun onBackPressed() {
        openMenu()
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
            val desiredSystemOrientation = when(currentLayoutConfiguration.orientation) {
                LayoutConfiguration.LayoutOrientation.FOLLOW_SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                LayoutConfiguration.LayoutOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                LayoutConfiguration.LayoutOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }

            binding.viewLayoutEditor.instantiateLayout(currentLayoutConfiguration)

            if (desiredSystemOrientation != requestedOrientation) {
                requestedOrientation = desiredSystemOrientation
            }
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

    private fun showScalingControls(currentScale: Float, maxSize: Int, minSize: Int, animate: Boolean = true) {
        binding.seekBarScaling.apply {
            max = maxSize
            progress = (currentScale * maxSize).toInt()
        }

        selectedViewMinSize = minSize

        if (areScalingControlsShown)
            return

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
        val instantiatedComponents = binding.viewLayoutEditor.getInstantiatedComponents()
        val componentsToShow = LayoutComponent.values().filterNot { instantiatedComponents.contains(it) }

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
}