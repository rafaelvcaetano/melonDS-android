package me.magnum.melonds.ui.common.componentbuilders

import android.content.Context
import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import me.magnum.melonds.ui.common.LayoutComponentViewBuilder

abstract class ScreenLayoutComponentViewBuilder : LayoutComponentViewBuilder() {
    override fun build(context: Context): View {
        return View(context).apply {
            background = ContextCompat.getDrawable(context, getBackgroundResource())
        }
    }

    override fun getAspectRatio() = 256f / 192

    @DrawableRes
    protected abstract fun getBackgroundResource(): Int
}