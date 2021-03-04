package me.magnum.melonds.ui.layouteditor

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ActivityLayoutEditorBinding
import me.magnum.melonds.databinding.DialogLayoutNameInputBinding
import me.magnum.melonds.domain.model.LayoutComponent
import me.magnum.melonds.impl.ScreenUnitsConverter
import me.magnum.melonds.utils.getLayoutComponentName
import javax.inject.Inject

@AndroidEntryPoint
class LayoutEditorActivity : AppCompatActivity() {
    companion object {
        const val KEY_LAYOUT_ID = "layout_id"
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
    private lateinit var binding: ActivityLayoutEditorBinding
    private var areBottomControlsShown = true
    private var areScalingControlsShown = true

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
        binding.viewLayoutEditor.setOnViewSelectedListener { _, scale ->
            hideBottomControls()
            showScalingControls(scale)
        }
        binding.viewLayoutEditor.setOnViewDeselectedListener {
            hideScalingControls()
        }
        binding.seekBarScaling.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.viewLayoutEditor.scaleSelectedView(progress / 10000f)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })

        setupFullscreen()
        instantiateLayout()
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
        viewModel.saveLayoutToCurrentConfiguration(binding.viewLayoutEditor.buildCurrentLayout(), orientation)
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

    private fun instantiateLayout() {
        val currentLayoutConfiguration = viewModel.getCurrentLayoutConfiguration()
        if (currentLayoutConfiguration == null)
            instantiateDefaultConfiguration()
        else
            binding.viewLayoutEditor.instantiateLayout(currentLayoutConfiguration)
    }

    private fun instantiateDefaultConfiguration() {
        val defaultLayout = viewModel.getDefaultLayoutConfiguration()
        viewModel.setCurrentLayoutConfiguration(defaultLayout)
        binding.viewLayoutEditor.instantiateLayout(defaultLayout)
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

    private fun showScalingControls(currentScale: Float, animate: Boolean = true) {
        binding.seekBarScaling.progress = (currentScale * 10000).toInt()

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

    private fun revertLayoutConfiguration() {
        viewModel.getInitialLayoutConfiguration()?.let {
            viewModel.setCurrentLayoutConfiguration(it)
            binding.viewLayoutEditor.instantiateLayout(it)
        }
    }
}