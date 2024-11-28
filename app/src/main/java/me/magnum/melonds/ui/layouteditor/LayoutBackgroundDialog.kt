package me.magnum.melonds.ui.layouteditor

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.magnum.melonds.R
import me.magnum.melonds.common.Schedulers
import me.magnum.melonds.databinding.DialogLayoutBackgroundsBinding
import me.magnum.melonds.domain.model.layout.BackgroundMode
import me.magnum.melonds.domain.model.ui.Orientation
import me.magnum.melonds.ui.backgrounds.BackgroundsActivity
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class LayoutBackgroundDialog : DialogFragment() {
    companion object {
        fun newInstance(): LayoutBackgroundDialog {
            return LayoutBackgroundDialog()
        }
    }

    @Inject
    lateinit var schedulers: Schedulers
    private lateinit var binding: DialogLayoutBackgroundsBinding
    private val viewModel: LayoutEditorViewModel by activityViewModels()

    private var currentBackgroundId: UUID? = null
    private lateinit var currentBackgroundMode: BackgroundMode

    private var backgroundNameDisposable: Disposable? = null

    private val portraitBackgroundSelector = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            val backgroundId = result.data?.getStringExtra(BackgroundsActivity.KEY_SELECTED_BACKGROUND_ID)?.let { UUID.fromString(it) }
            onBackgroundSelected(backgroundId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentLayout.collectLatest {
                    it?.let {
                        onBackgroundSelected(it.layout.backgroundId)
                        onBackgroundModeSelected(it.layout.backgroundMode)
                    }
                }
            }
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

        binding.layoutBackgroundName.setOnClickListener {
            val intent = Intent(requireContext(), BackgroundsActivity::class.java).apply {
                putExtra(BackgroundsActivity.KEY_INITIAL_BACKGROUND_ID, currentBackgroundId?.toString())
                putExtra(BackgroundsActivity.KEY_ORIENTATION_FILTER, Orientation.PORTRAIT.ordinal)
            }
            portraitBackgroundSelector.launch(intent)
        }
        binding.layoutBackgroundMode.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.background_mode)
                .setSingleChoiceItems(R.array.background_portrait_mode_options, BackgroundMode.entries.indexOf(currentBackgroundMode)) { dialog, which ->
                    val newMode = BackgroundMode.entries[which]
                    onBackgroundModeSelected(newMode)
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }

        binding.buttonBackgroundConfigOk.setOnClickListener {
            viewModel.saveBackgroundToCurrentConfiguration(
                currentBackgroundId,
                currentBackgroundMode,
            )
            dismiss()
        }
        binding.buttonBackgroundConfigCancel.setOnClickListener { dismiss() }
    }

    private fun onBackgroundSelected(backgroundId: UUID?) {
        currentBackgroundId = backgroundId
        backgroundNameDisposable?.dispose()

        if (backgroundId == null) {
            binding.textBackgroundName.setText(R.string.none)
        } else {
            backgroundNameDisposable = viewModel.getBackgroundName(backgroundId)
                .subscribeOn(schedulers.backgroundThreadScheduler)
                .observeOn(schedulers.uiThreadScheduler)
                .materialize()
                .subscribe { message ->
                    if (!message.isOnError) {
                        if (message.value != null) {
                            binding.textBackgroundName.text = message.value
                        } else {
                            // If no background was found, the it was probably deleted. Revert to None
                            onBackgroundSelected(null)
                        }
                    }
                }
        }
    }

    private fun onBackgroundModeSelected(mode: BackgroundMode) {
        val modeNames = requireContext().resources.getStringArray(R.array.background_portrait_mode_options)
        binding.textBackgroundMode.text = modeNames[BackgroundMode.entries.indexOf(mode)]
        currentBackgroundMode = mode
    }
}