package me.magnum.melonds.ui.layouteditor

import android.app.Dialog
import android.content.DialogInterface
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.magnum.melonds.R
import me.magnum.melonds.databinding.DialogLayoutBackgroundsBinding
import me.magnum.melonds.domain.model.layout.BackgroundMode
import me.magnum.melonds.ui.backgrounds.BackgroundsActivity
import java.util.UUID

@AndroidEntryPoint
class LayoutBackgroundDialog : DialogFragment() {
    companion object {
        fun newInstance(): LayoutBackgroundDialog {
            return LayoutBackgroundDialog()
        }
    }

    private lateinit var binding: DialogLayoutBackgroundsBinding
    private val viewModel: LayoutEditorViewModel by activityViewModels()

    private var backgroundNameJob: Job? = null

    private val portraitBackgroundSelector = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            val backgroundId = result.data?.getStringExtra(BackgroundsActivity.KEY_SELECTED_BACKGROUND_ID)?.let { UUID.fromString(it) }
            viewModel.setBackgroundPropertiesBackgroundId(backgroundId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.layoutBackgroundProperties.collectLatest {
                    it?.let {
                        onBackgroundSelected(it.backgroundId)
                        onBackgroundModeSelected(it.backgroundMode)
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

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        viewModel.resetBackgroundProperties()
    }

    override fun onStart() {
        super.onStart()
        isCancelable = true

        binding.layoutBackgroundName.setOnClickListener {
            val intent = Intent(requireContext(), BackgroundsActivity::class.java).apply {
                putExtra(BackgroundsActivity.KEY_INITIAL_BACKGROUND_ID, viewModel.layoutBackgroundProperties.value?.backgroundId?.toString())
            }
            portraitBackgroundSelector.launch(intent)
        }
        binding.layoutBackgroundMode.setOnClickListener {
            val currentBackgroundMode = viewModel.layoutBackgroundProperties.value?.backgroundMode
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.background_mode)
                .setSingleChoiceItems(R.array.background_portrait_mode_options, BackgroundMode.entries.indexOf(currentBackgroundMode)) { dialog, which ->
                    val newMode = BackgroundMode.entries[which]
                    viewModel.setBackgroundPropertiesBackgroundMode(newMode)
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }

        binding.buttonBackgroundConfigOk.setOnClickListener {
            viewModel.saveBackgroundToCurrentConfiguration()
            dismiss()
        }
        binding.buttonBackgroundConfigCancel.setOnClickListener { dismiss() }
    }

    private fun onBackgroundSelected(backgroundId: UUID?) {
        backgroundNameJob?.cancel()

        if (backgroundId == null) {
            binding.textBackgroundName.setText(R.string.none)
        } else {
            backgroundNameJob = lifecycleScope.launch {
                val backgroundName = viewModel.getBackgroundName(backgroundId)
                if (backgroundName != null) {
                    binding.textBackgroundName.text = backgroundName
                } else {
                    // If no background was found, the it was probably deleted. Revert to None
                    viewModel.setBackgroundPropertiesBackgroundId(null)
                }
            }
        }
    }

    private fun onBackgroundModeSelected(mode: BackgroundMode) {
        val modeNames = requireContext().resources.getStringArray(R.array.background_portrait_mode_options)
        binding.textBackgroundMode.text = modeNames[BackgroundMode.entries.indexOf(mode)]
    }
}