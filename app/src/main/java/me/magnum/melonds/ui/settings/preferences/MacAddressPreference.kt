package me.magnum.melonds.ui.settings.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import me.magnum.melonds.R
import me.magnum.melonds.databinding.DialogMacAddressEditorBinding
import java.util.*
import kotlin.random.Random
import kotlin.random.nextUBytes

@OptIn(ExperimentalUnsignedTypes::class)
class MacAddressPreference(context: Context?, attrs: AttributeSet?) : Preference(context, attrs) {
    companion object {
        private val MAC_PREFIX = listOf(0x0.toUByte(), 0x9u.toUByte(), 0xBFu.toUByte())
    }

    private val random = Random(System.nanoTime())
    private var currentMacAddress: List<UByte>? = null

    override fun onClick() {
        super.onClick()

        val binding = DialogMacAddressEditorBinding.inflate(LayoutInflater.from(context))
        currentMacAddress = getPersistedString(null)?.let { stringToMacAddress(it) }
        setMacAddressText(currentMacAddress, binding.textMacAddress)

        val dialog = AlertDialog.Builder(context)
                .setTitle(title)
                .setView(binding.root)
                .setPositiveButton(R.string.ok) { dialog, _ ->
                    val address = macAddressToString(currentMacAddress)
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
            val address = MAC_PREFIX + random.nextUBytes(3).toList()
            currentMacAddress = address
            setMacAddressText(address, binding.textMacAddress)
        }
    }

    private fun stringToMacAddress(string: String): List<UByte> {
        return string.split(":").map { it.toUByte(16) }
    }

    private fun macAddressToString(address: Collection<UByte>?): String? {
        if (address == null) {
            return null
        }

        return address.joinToString(":") { it.toString(16).padStart(2, '0') }.toUpperCase(Locale.getDefault())
    }

    private fun setMacAddressText(macAddress: Collection<UByte>?, textView: TextView) {
        if (macAddress == null) {
            textView.text = context.getString(R.string.not_set)
        } else {
            val stringMacAddress = macAddressToString(macAddress)
            textView.text = stringMacAddress
        }
    }
}