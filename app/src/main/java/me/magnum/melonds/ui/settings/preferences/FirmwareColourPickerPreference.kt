package me.magnum.melonds.ui.settings.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.children
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import me.magnum.melonds.R
import me.magnum.melonds.databinding.DialogFirmwareColourPickerBinding
import me.magnum.melonds.domain.model.FirmwareColour

class FirmwareColourPickerPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    companion object {
        private val colorMapper = mapOf(
                FirmwareColour.GRAY to 0x61829A,
                FirmwareColour.BROWN to 0xBA4900,
                FirmwareColour.RED to 0xFB0018,
                FirmwareColour.PINK to 0xFB8AFB,
                FirmwareColour.ORANGE to 0xFB9200,
                FirmwareColour.YELLOW to 0xF3E300,
                FirmwareColour.LIME to 0xAAFB00,
                FirmwareColour.GREEN to 0x00FB00,
                FirmwareColour.DARK_GREEN to 0x00A238,
                FirmwareColour.TURQUOISE to 0x49DB8A,
                FirmwareColour.LIGHT_BLUE to 0x30BAF3,
                FirmwareColour.BLUE to 0x0059F3,
                FirmwareColour.DARK_BLUE to 0x000092,
                FirmwareColour.PURPLE to 0x8A00D3,
                FirmwareColour.VIOLET to 0xD300EB,
                FirmwareColour.FUCHSIA to 0xFB0092
        )
    }

    private lateinit var viewSelectedColour: View

    init {
        widgetLayoutResource = R.layout.preference_firmware_colour_picker_colour
    }

    override fun onClick() {
        super.onClick()

        val binding = DialogFirmwareColourPickerBinding.inflate(LayoutInflater.from(context))

        val alertDialog = AlertDialog.Builder(context)
                .setTitle(title)
                .setView(binding.root)
                .setNegativeButton(R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()

        binding.layoutGridColours.children.flatMap { (it as ViewGroup).children }.forEach {
            it.setOnClickListener {  view ->
                val selectedColour = (view.tag as String).toInt()
                updateSelectedColour(selectedColour)
                if (callChangeListener(selectedColour)) {
                    persistInt(selectedColour)
                }
                alertDialog.dismiss()
            }
        }
    }

    private fun updateSelectedColour(selectedColour: Int) {
        val firmwareColour = FirmwareColour.entries[selectedColour]
        colorMapper[firmwareColour]?.let {
            val colourWithAlpha = (0xFF000000 or it.toLong())
            viewSelectedColour.setBackgroundColor(colourWithAlpha.toInt())
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        viewSelectedColour = holder.findViewById(R.id.viewSelectedColour)
        updateSelectedColour(getPersistedInt(0))
    }
}