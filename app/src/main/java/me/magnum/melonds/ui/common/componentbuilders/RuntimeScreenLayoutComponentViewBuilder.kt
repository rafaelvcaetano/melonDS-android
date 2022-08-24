package me.magnum.melonds.ui.common.componentbuilders

import android.content.Context
import android.graphics.drawable.Drawable

class RuntimeScreenLayoutComponentViewBuilder : ScreenLayoutComponentViewBuilder() {
    override fun getBackgroundDrawable(context: Context): Drawable? = null
}