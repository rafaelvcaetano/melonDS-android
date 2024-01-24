package me.magnum.melonds.ui.layouteditor

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.disposables.Disposable
import me.magnum.melonds.R
import me.magnum.melonds.common.Schedulers
import me.magnum.melonds.databinding.DialogLayoutBackgroundsBinding
import me.magnum.melonds.domain.model.BackgroundMode
import me.magnum.melonds.domain.model.LayoutConfiguration
import me.magnum.melonds.domain.model.Orientation
import me.magnum.melonds.ui.backgrounds.BackgroundsActivity
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class LayoutBackgroundsDialog : DialogFragment() {
    companion object {
        private const val KEY_PORTRAIT_BACKGROUND_ID = "portrait_background_id"
        private const val KEY_PORTRAIT_BACKGROUND_MODE = "portrait_background_mode"
        private const val KEY_LANDSCAPE_BACKGROUND_ID = "landscape_background_id"
        private const val KEY_LANDSCAPE_BACKGROUND_MODE = "landscape_background_mode"

        fun newInstance(layoutConfiguration: LayoutConfiguration): LayoutBackgroundsDialog {
            return LayoutBackgroundsDialog().apply {
                arguments = bundleOf(
                    KEY_PORTRAIT_BACKGROUND_ID to layoutConfiguration.portraitLayout.backgroundId?.toString(),
                    KEY_PORTRAIT_BACKGROUND_MODE to layoutConfiguration.portraitLayout.backgroundMode.ordinal,
                    KEY_LANDSCAPE_BACKGROUND_ID to layoutConfiguration.landscapeLayout.backgroundId?.toString(),
                    KEY_LANDSCAPE_BACKGROUND_MODE to layoutConfiguration.landscapeLayout.backgroundMode.ordinal
                )
            }
        }
    }

    @Inject
    lateinit var schedulers: Schedulers
    private lateinit var binding: DialogLayoutBackgroundsBinding
    private val viewModel: LayoutEditorViewModel by activityViewModels()

    private var currentPortraitBackgroundId: UUID? = null
    private lateinit var currentPortraitBackgroundMode: BackgroundMode
    private var currentLandscapeBackgroundId: UUID? = null
    private lateinit var currentLandscapeBackgroundMode: BackgroundMode

    private var portraitBackgroundNameDisposable: Disposable? = null
    private var landscapeBackgroundNameDisposable: Disposable? = null

    private val portraitBackgroundSelector = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            val backgroundId = result.data?.getStringExtra(BackgroundsActivity.KEY_SELECTED_BACKGROUND_ID)?.let { UUID.fromString(it) }
            onPortraitBackgroundSelected(backgroundId)
        }
    }
    private val landscapeBackgroundSelector = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            val backgroundId = result.data?.getStringExtra(BackgroundsActivity.KEY_SELECTED_BACKGROUND_ID)?.let { UUID.fromString(it) }
            onLandscapeBackgroundSelected(backgroundId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            currentPortraitBackgroundId = savedInstanceState.getString(KEY_PORTRAIT_BACKGROUND_ID, null)?.let { UUID.fromString(it) }
            currentPortraitBackgroundMode = BackgroundMode.entries[savedInstanceState.getInt(KEY_PORTRAIT_BACKGROUND_MODE, 0)]
            currentLandscapeBackgroundId = savedInstanceState.getString(KEY_LANDSCAPE_BACKGROUND_ID, null)?.let { UUID.fromString(it) }
            currentLandscapeBackgroundMode = BackgroundMode.entries[savedInstanceState.getInt(KEY_LANDSCAPE_BACKGROUND_MODE, 0)]
        } else {
            currentPortraitBackgroundId = arguments?.getString(KEY_PORTRAIT_BACKGROUND_ID, null)?.let { UUID.fromString(it) }
            currentPortraitBackgroundMode = BackgroundMode.entries[arguments?.getInt(KEY_PORTRAIT_BACKGROUND_MODE, 0) ?: 0]
            currentLandscapeBackgroundId = arguments?.getString(KEY_LANDSCAPE_BACKGROUND_ID, null)?.let { UUID.fromString(it) }
            currentLandscapeBackgroundMode = BackgroundMode.entries[arguments?.getInt(KEY_LANDSCAPE_BACKGROUND_MODE, 0) ?: 0]
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogLayoutBackgroundsBinding.inflate(layoutInflater)

        return AlertDialog.Builder(requireContext())
                .setView(binding.root)
                .setCancelable(true)
                .create()
    }

    override fun onStart() {
        super.onStart()
        isCancelable = true

        binding.layoutPortraitBackground.setOnClickListener {
            val intent = Intent(requireContext(), BackgroundsActivity::class.java).apply {
                putExtra(BackgroundsActivity.KEY_INITIAL_BACKGROUND_ID, currentPortraitBackgroundId?.toString())
                putExtra(BackgroundsActivity.KEY_ORIENTATION_FILTER, Orientation.PORTRAIT.ordinal)
            }
            portraitBackgroundSelector.launch(intent)
        }
        binding.layoutPortraitBackgroundMode.setOnClickListener {
            AlertDialog.Builder(requireContext())
                    .setTitle(R.string.portrait_background_mode)
                    .setSingleChoiceItems(R.array.background_portrait_mode_options, BackgroundMode.PORTRAIT_MODES.indexOf(currentPortraitBackgroundMode)) { dialog, which ->
                        val newMode = BackgroundMode.PORTRAIT_MODES[which]
                        onPortraitBackgroundModeSelected(newMode)
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.cancel) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
        }
        binding.layoutLandscapeBackground.setOnClickListener {
            val intent = Intent(requireContext(), BackgroundsActivity::class.java).apply {
                putExtra(BackgroundsActivity.KEY_INITIAL_BACKGROUND_ID, currentLandscapeBackgroundId?.toString())
                putExtra(BackgroundsActivity.KEY_ORIENTATION_FILTER, Orientation.LANDSCAPE.ordinal)
            }
            landscapeBackgroundSelector.launch(intent)
        }
        binding.layoutLandscapeBackgroundMode.setOnClickListener {
            AlertDialog.Builder(requireContext())
                    .setTitle(R.string.landscape_background_mode)
                    .setSingleChoiceItems(R.array.background_landscape_mode_options, BackgroundMode.LANDSCAPE_MODES.indexOf(currentLandscapeBackgroundMode)) { dialog, which ->
                        val newMode = BackgroundMode.LANDSCAPE_MODES[which]
                        onLandscapeBackgroundModeSelected(newMode)
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
        }

        binding.buttonBackgroundConfigOk.setOnClickListener {
            viewModel.saveBackgroundsToCurrentConfiguration(
                    currentPortraitBackgroundId,
                    currentPortraitBackgroundMode,
                    currentLandscapeBackgroundId,
                    currentLandscapeBackgroundMode
            )
            dismiss()
        }
        binding.buttonBackgroundConfigCancel.setOnClickListener { dismiss() }

        onPortraitBackgroundSelected(currentPortraitBackgroundId)
        onPortraitBackgroundModeSelected(currentPortraitBackgroundMode)
        onLandscapeBackgroundSelected(currentLandscapeBackgroundId)
        onLandscapeBackgroundModeSelected(currentLandscapeBackgroundMode)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PORTRAIT_BACKGROUND_ID, currentPortraitBackgroundId?.toString())
        outState.putInt(KEY_PORTRAIT_BACKGROUND_MODE, currentPortraitBackgroundMode.ordinal)
        outState.putString(KEY_LANDSCAPE_BACKGROUND_ID, currentLandscapeBackgroundId?.toString())
        outState.putInt(KEY_LANDSCAPE_BACKGROUND_MODE, currentLandscapeBackgroundMode.ordinal)
    }

    private fun onPortraitBackgroundSelected(backgroundId: UUID?) {
        currentPortraitBackgroundId = backgroundId
        portraitBackgroundNameDisposable?.dispose()

        if (backgroundId == null) {
            binding.textBackgroundPortrait.setText(R.string.none)
        } else {
            portraitBackgroundNameDisposable = viewModel.getBackgroundName(backgroundId)
                    .subscribeOn(schedulers.backgroundThreadScheduler)
                    .observeOn(schedulers.uiThreadScheduler)
                    .materialize()
                    .subscribe { message ->
                        if (!message.isOnError) {
                            if (message.value != null) {
                                binding.textBackgroundPortrait.text = message.value
                            } else {
                                // If no background was found, the it was probably deleted. Revert to None
                                onPortraitBackgroundSelected(null)
                            }
                        }
                    }
        }
    }

    private fun onPortraitBackgroundModeSelected(mode: BackgroundMode) {
        val modeNames = requireContext().resources.getStringArray(R.array.background_portrait_mode_options)
        binding.textBackgroundPortraitMode.text = modeNames[BackgroundMode.PORTRAIT_MODES.indexOf(mode)]
        currentPortraitBackgroundMode = mode
    }

    private fun onLandscapeBackgroundSelected(backgroundId: UUID?) {
        currentLandscapeBackgroundId = backgroundId
        landscapeBackgroundNameDisposable?.dispose()

        if (backgroundId == null) {
            binding.textLandscapeBackground.setText(R.string.none)
        } else {
            landscapeBackgroundNameDisposable = viewModel.getBackgroundName(backgroundId)
                    .subscribeOn(schedulers.backgroundThreadScheduler)
                    .observeOn(schedulers.uiThreadScheduler)
                    .materialize()
                    .subscribe { message ->
                        if (!message.isOnError) {
                            if (message.value != null) {
                                binding.textLandscapeBackground.text = message.value
                            } else {
                                // If no background was found, the it was probably deleted. Revert to None
                                onLandscapeBackgroundSelected(null)
                            }
                        }
                    }
        }
    }

    private fun onLandscapeBackgroundModeSelected(mode: BackgroundMode) {
        val modeNames = requireContext().resources.getStringArray(R.array.background_landscape_mode_options)
        binding.textBackgroundLandscapeMode.text = modeNames[BackgroundMode.LANDSCAPE_MODES.indexOf(mode)]
        currentLandscapeBackgroundMode = mode
    }
}