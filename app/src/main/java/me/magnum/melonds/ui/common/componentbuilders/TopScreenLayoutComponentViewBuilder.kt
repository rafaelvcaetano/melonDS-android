package me.magnum.melonds.ui.common.componentbuilders

import android.content.Context
import androidx.core.content.ContextCompat
import me.magnum.melonds.R

class TopScreenLayoutComponentViewBuilder : ScreenLayoutComponentViewBuilder() {
    override fun getBackgroundDrawable(context: Context) = ContextCompat.getDrawable(context, R.drawable.background_top_screen)
}