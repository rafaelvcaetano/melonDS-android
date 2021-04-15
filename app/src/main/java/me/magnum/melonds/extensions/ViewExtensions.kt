package me.magnum.melonds.extensions

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.children
import me.magnum.melonds.domain.model.BackgroundMode

fun View.setViewEnabledRecursive(enabled: Boolean) {
    this.isEnabled = enabled
    if (this is ViewGroup) {
        this.children.forEach { it.setViewEnabledRecursive(enabled) }
    }
}

fun ImageView.setBackgroundMode(mode: BackgroundMode) {
    when (mode) {
        BackgroundMode.STRETCH -> this.scaleType = ImageView.ScaleType.FIT_XY
        BackgroundMode.FIT_CENTER -> this.scaleType = ImageView.ScaleType.FIT_CENTER
        BackgroundMode.FIT_TOP -> this.scaleType = ImageView.ScaleType.FIT_START
        BackgroundMode.FIT_BOTTOM -> this.scaleType = ImageView.ScaleType.FIT_END
        BackgroundMode.FIT_LEFT -> this.scaleType = ImageView.ScaleType.FIT_START
        BackgroundMode.FIT_RIGHT -> this.scaleType = ImageView.ScaleType.FIT_END
    }
}