package me.magnum.melonds.ui.layouteditor

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
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
        private const val KEY_LAYOUT_ORIENTATION = "layout_orientation"
        private const val KEY_CUSTOM_OPACITY = "custom_opacity"
        private const val KEY_LAYOUT_OPACITY = "layout_opacity"

        fun newInstance(layoutConfiguration: LayoutConfiguration): LayoutPropertiesDialog {
            return LayoutPropertiesDialog().apply {
                arguments = bundleOf(
                    KEY_LAYOUT_NAME to layoutConfiguration.name,
                    KEY_LAYOUT_ORIENTATION to layoutConfiguration.orientation.ordinal,
                    KEY_CUSTOM_OPACITY to layoutConfiguration.useCustomOpacity,
                    KEY_LAYOUT_OPACITY to layoutConfiguration.opacity
                )
            }
        }
    }

    private lateinit var binding: DialogLayoutPropertiesBinding
    private val viewModel: LayoutEditorViewModel by activityViewModels()

    private var layoutName: String? = null
    private var layoutOrientation = LayoutConfiguration.LayoutOrientation.FOLLOW_SYSTEM
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
            layoutOrientation = LayoutConfiguration.LayoutOrientation.entries[savedInstanceState.getInt(KEY_LAYOUT_ORIENTATION)]
            useCustomOpacity = savedInstanceState.getBoolean(KEY_CUSTOM_OPACITY)
            layoutOpacity = savedInstanceState.getInt(KEY_LAYOUT_OPACITY)
        } else {
            layoutName = arguments?.getString(KEY_LAYOUT_NAME)
            layoutOrientation = LayoutConfiguration.LayoutOrientation.entries[arguments?.getInt(KEY_LAYOUT_ORIENTATION) ?: 0]
            useCustomOpacity = arguments?.getBoolean(KEY_CUSTOM_OPACITY) ?: true
            layoutOpacity = arguments?.getInt(KEY_LAYOUT_OPACITY) ?: 0
        }
    }

    override fun onStart() {
        super.onStart()
        isCancelable = true

        binding.layoutName.setOnClickListener {
            TextInputDialog.Builder()
                    .setTitle(getString(R.string.layout_name))
                    .setText(layoutName)
                    .setOnConfirmListener {
                        setLayoutName(it)
                    }
                    .build()
                    .show(childFragmentManager, null)
        }

        binding.layoutOrientation.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.layout_orientation)
                .setSingleChoiceItems(R.array.layout_orientation_options, LayoutConfiguration.LayoutOrientation.entries.indexOf(layoutOrientation)) { dialog, which ->
                    val newOrientation = LayoutConfiguration.LayoutOrientation.entries[which]
                    setLayoutOrientation(newOrientation)
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }

        binding.switchUseDefaultOpacity.setOnCheckedChangeListener { _, isChecked ->
            setUseCustomOpacity(!isChecked)
        }
        binding.layoutUseDefaultOpacity.setOnClickListener {
            binding.switchUseDefaultOpacity.toggle()
        }

        binding.buttonPropertiesOk.setOnClickListener {
            val opacity = binding.seekbarOpacity.progress
            viewModel.savePropertiesToCurrentConfiguration(layoutName, layoutOrientation, useCustomOpacity, opacity)
            dismiss()
        }
        binding.buttonPropertiesCancel.setOnClickListener {
            dismiss()
        }

        setLayoutName(layoutName)
        setLayoutOrientation(layoutOrientation)
        setUseCustomOpacity(useCustomOpacity)
        binding.switchUseDefaultOpacity.isChecked = !useCustomOpacity
        binding.seekbarOpacity.progress = layoutOpacity
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_LAYOUT_NAME, layoutName)
        outState.putInt(KEY_LAYOUT_ORIENTATION, layoutOrientation.ordinal)
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

    private fun setLayoutOrientation(orientation: LayoutConfiguration.LayoutOrientation) {
        val orientationNames = requireContext().resources.getStringArray(R.array.layout_orientation_options)
        binding.textOrientation.text = orientationNames[LayoutConfiguration.LayoutOrientation.entries.indexOf(orientation)]
        layoutOrientation = orientation
    }

    private fun setUseCustomOpacity(useCustomOpacity: Boolean) {
        this.useCustomOpacity = useCustomOpacity
        binding.layoutOpacity.setViewEnabledRecursive(useCustomOpacity)
    }
}