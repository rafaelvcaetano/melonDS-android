package me.magnum.melonds.ui.settings.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import me.magnum.melonds.R
import me.magnum.melonds.databinding.DialogMacAddressEditorBinding
import me.magnum.melonds.domain.model.MacAddress
import kotlin.random.Random

class MacAddressPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    private val random = Random(System.nanoTime())
    private var currentMacAddress: MacAddress? = null

    override fun onClick() {
        super.onClick()

        val binding = DialogMacAddressEditorBinding.inflate(LayoutInflater.from(context))
        currentMacAddress = getPersistedString(null)?.let { MacAddress.fromString(it) }
        if (currentMacAddress?.isValid() == false) {
            currentMacAddress = null
        }
        setMacAddressText(currentMacAddress, binding.textMacAddress)

        val dialog = AlertDialog.Builder(context)
                .setTitle(title)
                .setView(binding.root)
                .setPositiveButton(R.string.ok) { dialog, _ ->
                    val address = currentMacAddress?.toString()
                    if (callChangeListener(address)) {
                        persistString(address)
                    }
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                .setNeutralButton(R.string.generate_new_mac_address, null)
                .show()

        // Set listener after creating dialog to prevent dismiss on click
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            currentMacAddress = MacAddress.randomDsAddress(random)
            setMacAddressText(currentMacAddress, binding.textMacAddress)
        }
    }

    private fun setMacAddressText(macAddress: MacAddress?, textView: TextView) {
        if (macAddress == null) {
            textView.text = context.getString(R.string.not_set)
        } else {
            val stringMacAddress = macAddress.toString()
            textView.text = stringMacAddress
        }
    }
}