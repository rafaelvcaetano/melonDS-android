package me.magnum.melonds.ui.common

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import me.magnum.melonds.R
import me.magnum.melonds.databinding.DialogTextInputBinding

class TextInputDialog : DialogFragment() {
    companion object {
        private const val KEY_TITLE = "title"
        private const val KEY_TEXT = "text"
    }

    private lateinit var binding: DialogTextInputBinding

    private var title: String? = null
    private var startText: String? = null
    private var onConfirmListener: ((String) -> Unit)? = null
    private var onCancelListener: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            title = savedInstanceState.getString(KEY_TITLE)
            startText = savedInstanceState.getString(KEY_TEXT)
        } else {
            title = arguments?.getString(KEY_TITLE)
            startText = arguments?.getString(KEY_TEXT)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogTextInputBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setView(binding.root)
                .setPositiveButton(R.string.ok) { _, _ ->
                    val text = binding.editText.text.toString()
                    onConfirmListener?.invoke(text)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()

        binding.editText.doOnTextChanged { text, _, _, _ ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = (text?.length ?: 0) > 0
        }

        return dialog
    }

    override fun onStart() {
        super.onStart()
        isCancelable = true

        binding.editText.apply {
            setText(startText ?: "", TextView.BufferType.EDITABLE)
            requestFocus()
            setSelection(text.toString().length)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_TITLE, title)
        outState.putString(KEY_TEXT, binding.editText.text.toString())
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onCancelListener?.invoke()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        onCancelListener?.invoke()
    }

    private fun setOnConfirmListener(listener: (String) -> Unit) {
        onConfirmListener = listener
    }

    private fun setOnCancelListener(listener: () -> Unit) {
        onCancelListener = listener
    }

    class Builder {
        private var title: String? = null
        private var text: String? = null
        private var onConfirmListener: ((String) -> Unit)? = null
        private var onCancelListener: (() -> Unit)? = null

        fun setTitle(title: String?): Builder {
            this.title = title
            return this
        }

        fun setText(text: String?): Builder {
            this.text = text
            return this
        }

        fun setOnConfirmListener(listener: (String) -> Unit): Builder {
            onConfirmListener = listener
            return this
        }

        fun setOnCancelListener(listener: () -> Unit): Builder {
            onCancelListener = listener
            return this
        }

        fun build(): TextInputDialog {
            return TextInputDialog().apply {
                this@Builder.onConfirmListener?.let { setOnConfirmListener(it) }
                this@Builder.onCancelListener?.let { setOnCancelListener(it) }
                arguments = bundleOf(
                    KEY_TITLE to this@Builder.title,
                    KEY_TEXT to this@Builder.text
                )
            }
        }
    }
}