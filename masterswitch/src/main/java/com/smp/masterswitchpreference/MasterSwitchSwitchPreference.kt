package com.smp.masterswitchpreference

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat

internal class MasterSwitchSwitchPreference : SwitchPreferenceCompat {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    internal lateinit var attrs: MasterSwitchPreferenceAttrs
    
    private lateinit var background: View
    private lateinit var switch: SwitchCompat
    private lateinit var text: TextView

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        fun applyTextColor(textView: TextView) {
            textView.setTextColor(attrs.switchTextColor)
        }

        fun applySwitchColor() {
            switch.thumbDrawable?.let {
                DrawableCompat.setTint(it, attrs.switchThumbColor)
            }
            switch.trackDrawable?.let {
                DrawableCompat.setTint(it, attrs.switchTrackColor)
            }
        }
        holder.itemView.let { view ->
            background = view
            switch = view.findViewById(R.id.switchWidget)
            text = view.findViewById(android.R.id.title)

            applySwitchColor()
            if (switch.isChecked) {
                applySwitchOnBackgroundColor()
            } else {
                applySwitchOffBackgroundColor()
            }
            applyTextColor(text)
        }
    }

    internal fun applySwitchOnBackgroundColor() {
        background.setBackgroundColor(attrs.switchOnBackgroundColor)
    }

    internal fun applySwitchOffBackgroundColor() {
        background.setBackgroundColor(attrs.switchOffBackgroundColor)
    }
}

