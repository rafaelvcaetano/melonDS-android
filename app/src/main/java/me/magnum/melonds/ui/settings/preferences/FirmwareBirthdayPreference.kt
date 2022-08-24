package me.magnum.melonds.ui.settings.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import me.magnum.melonds.R
import me.magnum.melonds.databinding.DialogFirmwareBirthdayBinding
import java.text.NumberFormat

class FirmwareBirthdayPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    companion object {
        private val daysInMonth = mapOf(
                1 to 31,
                2 to 29,
                3 to 31,
                4 to 30,
                5 to 31,
                6 to 30,
                7 to 31,
                8 to 31,
                9 to 30,
                10 to 31,
                11 to 30,
                12 to 31
        )

        private val numberFormat = NumberFormat.getNumberInstance().apply {
            minimumIntegerDigits = 2
        }
    }

    override fun onClick() {
        super.onClick()
        val binding = DialogFirmwareBirthdayBinding.inflate(LayoutInflater.from(context))

        AlertDialog.Builder(context)
                .setTitle(title)
                .setView(binding.root)
                .setPositiveButton(R.string.ok) { dialog, _ ->
                    val day = binding.textBirthdayDay.text.toString().toInt()
                    val month = binding.textBirthdayMonth.text.toString().toInt()
                    val birthday = "${numberFormat.format(day)}/${numberFormat.format(month)}"
                    if (callChangeListener(birthday)) {
                        persistString(birthday)
                    }
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()

        val currentBirthday = getPersistedString("01/01")
        val parts = currentBirthday.split("/")
        if (parts.size != 2) {
            setNumberFormatted(binding.textBirthdayDay, 1)
            setNumberFormatted(binding.textBirthdayMonth, 1)
        } else {
            setNumberFormatted(binding.textBirthdayDay, parts[0].toIntOrNull() ?: 1)
            setNumberFormatted(binding.textBirthdayMonth, parts[1].toIntOrNull() ?: 1)
        }

        binding.buttonBirthdayDayIncrease.setOnClickListener {
            val day = binding.textBirthdayDay.text.toString().toIntOrNull() ?: 0
            val month = binding.textBirthdayMonth.text.toString().toIntOrNull() ?: 1
            val newDay = coerceDayForMonth(day + 1, month, true)
            setNumberFormatted(binding.textBirthdayDay, newDay)
        }
        binding.buttonBirthdayDayDecrease.setOnClickListener {
            val day = binding.textBirthdayDay.text.toString().toIntOrNull() ?: 2
            val month = binding.textBirthdayMonth.text.toString().toIntOrNull() ?: 1
            val newDay = coerceDayForMonth(day - 1, month, true)
            setNumberFormatted(binding.textBirthdayDay, newDay)
        }
        binding.buttonBirthdayMonthIncrease.setOnClickListener {
            val day = binding.textBirthdayDay.text.toString().toIntOrNull() ?: 1
            val month = binding.textBirthdayMonth.text.toString().toIntOrNull() ?: 0
            val newMonth = coerceMonth(month + 1)
            val newDay = coerceDayForMonth(day, newMonth, false)

            setNumberFormatted(binding.textBirthdayMonth, newMonth)
            if (newDay != day) {
                setNumberFormatted(binding.textBirthdayDay, newDay)
            }
        }
        binding.buttonBirthdayMonthDecrease.setOnClickListener {
            val day = binding.textBirthdayDay.text.toString().toIntOrNull() ?: 1
            val month = binding.textBirthdayMonth.text.toString().toIntOrNull() ?: 2
            val newMonth = coerceMonth(month - 1)
            val newDay = coerceDayForMonth(day, newMonth, false)

            setNumberFormatted(binding.textBirthdayMonth, newMonth)
            if (newDay != day) {
                setNumberFormatted(binding.textBirthdayDay, newDay)
            }
        }
    }

    private fun coerceDayForMonth(day: Int, month: Int, loop: Boolean): Int {
        val daysInMonth = daysInMonth[month] ?: 1
        return if (loop) {
            when {
                day > daysInMonth -> 1
                day < 1 -> daysInMonth
                else -> day
            }
        } else {
            day.coerceIn(1, daysInMonth)
        }
    }

    private fun coerceMonth(month: Int): Int {
        return when {
            month < 1 -> 12
            month > 12 -> 1
            else -> month
        }
    }

    private fun setNumberFormatted(view: TextView, value: Int) {
        val formattedValue = numberFormat.format(value)
        view.text = formattedValue.toString()
    }
}