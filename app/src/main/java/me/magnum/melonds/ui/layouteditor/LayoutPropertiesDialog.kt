package me.magnum.melonds.ui.layouteditor

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import me.magnum.melonds.R
import me.magnum.melonds.databinding.DialogLayoutPropertiesBinding
import me.magnum.melonds.domain.model.LayoutConfiguration
import me.magnum.melonds.extensions.setViewEnabledRecursive
import me.magnum.melonds.ui.common.TextInputDialog

class LayoutPropertiesDialog : DialogFragment() {
    companion object {
        private const val KEY_LAYOUT_NAME = "layout_name"
        private const val KEY_CUSTOM_OPACITY = "custom_opacity"
        private const val KEY_LAYOUT_OPACITY = "layout_opacity"

        fun newInstance(layoutConfiguration: LayoutConfiguration): LayoutPropertiesDialog {
            return LayoutPropertiesDialog().apply {
                arguments = Bundle().apply {
                    putString(KEY_LAYOUT_NAME, layoutConfiguration.name)
                    putBoolean(KEY_CUSTOM_OPACITY, layoutConfiguration.useCustomOpacity)
                    putInt(KEY_LAYOUT_OPACITY, layoutConfiguration.opacity)
                }
            }
        }
    }

    private lateinit var binding: DialogLayoutPropertiesBinding
    private val viewModel: LayoutEditorViewModel by activityViewModels()

    private var layoutName: String? = null
    private var useCustomOpacity = false
    private var layoutOpacity = 0

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogLayoutPropertiesBinding.inflate(layoutInflater)

        return AlertDialog.Builder(requireContext())
                .setView(binding.root)
                .setCancelable(true)
                .create()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            layoutName = savedInstanceState.getString(KEY_LAYOUT_NAME)
            useCustomOpacity = savedInstanceState.getBoolean(KEY_CUSTOM_OPACITY)
            layoutOpacity = savedInstanceState.getInt(KEY_LAYOUT_OPACITY)
        } else {
            layoutName = arguments?.getString(KEY_LAYOUT_NAME)
            useCustomOpacity = arguments?.getBoolean(KEY_CUSTOM_OPACITY) ?: true
            layoutOpacity = arguments?.getInt(KEY_LAYOUT_OPACITY) ?: 0
        }
    }

    override fun onStart() {
        super.onStart()
        isCancelable = true

        binding.layoutName.setOnClickListener {
            TextInputDialog.Builder()
                    .setText(layoutName)
                    .setOnConfirmListener {
                        setLayoutName(it)
                    }
                    .build()
                    .show(childFragmentManager, null)
        }
        binding.switchUseDefaultOpacity.setOnCheckedChangeListener { _, isChecked ->
            setUseCustomOpacity(!isChecked)
        }
        binding.layoutUseDefaultOpacity.setOnClickListener {
            binding.switchUseDefaultOpacity.toggle()
        }

        binding.buttonPropertiesOk.setOnClickListener {
            val opacity = binding.seekbarOpacity.progress
            viewModel.savePropertiesToCurrentConfiguration(layoutName, useCustomOpacity, opacity)
            dismiss()
        }
        binding.buttonPropertiesCancel.setOnClickListener {
            dismiss()
        }

        setLayoutName(layoutName)
        setUseCustomOpacity(useCustomOpacity)
        binding.switchUseDefaultOpacity.isChecked = !useCustomOpacity
        binding.seekbarOpacity.progress = layoutOpacity
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_LAYOUT_NAME, layoutName)
        outState.putBoolean(KEY_CUSTOM_OPACITY, useCustomOpacity)
        outState.putInt(KEY_LAYOUT_OPACITY, binding.seekbarOpacity.progress)
    }

    private fun setLayoutName(name: String?) {
        if (name.isNullOrEmpty()) {
            binding.textName.setText(R.string.not_set)
            layoutName = null
        } else {
            binding.textName.text = name
            layoutName = name
        }
    }

    private fun setUseCustomOpacity(useCustomOpacity: Boolean) {
        this.useCustomOpacity = useCustomOpacity
        binding.layoutOpacity.setViewEnabledRecursive(useCustomOpacity)
    }
}