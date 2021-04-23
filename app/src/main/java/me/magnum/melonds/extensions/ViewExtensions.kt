package me.magnum.melonds.extensions

import android.content.Context
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
    val drawableWidth = this.drawable.intrinsicWidth
    val drawableHeight = this.drawable.intrinsicHeight

    val viewWidth = this.width - this.paddingLeft - this.paddingRight
    val viewHeight = this.height - this.paddingStart - this.paddingLeft

    val drawableAspectRatio = drawableWidth / drawableHeight.toFloat()
    val viewAspectRatio = viewWidth / viewHeight.toFloat()

    when (mode) {
        BackgroundMode.STRETCH -> this.scaleType = ImageView.ScaleType.FIT_XY
        BackgroundMode.FIT_CENTER -> this.scaleType = ImageView.ScaleType.FIT_CENTER
        BackgroundMode.FIT_TOP -> {
            this.scaleType = ImageView.ScaleType.MATRIX

            if (viewAspectRatio > drawableAspectRatio) {
                this.scaleType = ImageView.ScaleType.FIT_CENTER
            } else {
                this.scaleType = ImageView.ScaleType.FIT_START
            }
        }
        BackgroundMode.FIT_BOTTOM -> {
            if (viewAspectRatio > drawableAspectRatio) {
                this.scaleType = ImageView.ScaleType.FIT_CENTER
            } else {
                this.scaleType = ImageView.ScaleType.FIT_END
            }
        }
        BackgroundMode.FIT_LEFT -> {
            if (viewAspectRatio > drawableAspectRatio) {
                this.scaleType = getFitLeftScaleType(this.context)
            } else {
                this.scaleType = ImageView.ScaleType.FIT_CENTER
            }
        }
        BackgroundMode.FIT_RIGHT -> {
            if (viewAspectRatio > drawableAspectRatio) {
                this.scaleType = getFitRightScaleType(this.context)
            } else {
                this.scaleType = ImageView.ScaleType.FIT_CENTER
            }
        }
    }
}

private fun getFitLeftScaleType(context: Context): ImageView.ScaleType {
    return if (context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_LTR) {
        ImageView.ScaleType.FIT_START
    } else {
        ImageView.ScaleType.FIT_END
    }
}

private fun getFitRightScaleType(context: Context): ImageView.ScaleType {
    return if (context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_LTR) {
        ImageView.ScaleType.FIT_END
    } else {
        ImageView.ScaleType.FIT_START
    }
}