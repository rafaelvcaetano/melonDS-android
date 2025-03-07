package me.magnum.melonds.ui.emulator.input.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

class ToggleableImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    var enabledDrawable: Int? = null
    var disabledDrawable: Int? = null

    fun setToggleState(enabled: Boolean) {
        val drawable = if (enabled) enabledDrawable else disabledDrawable
        if (drawable != null) {
            setImageResource(drawable)
        }
    }
}