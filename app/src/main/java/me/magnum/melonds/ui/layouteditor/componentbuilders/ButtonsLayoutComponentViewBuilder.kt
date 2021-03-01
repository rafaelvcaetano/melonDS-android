package me.magnum.melonds.ui.layouteditor.componentbuilders

import android.content.Context
import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat
import me.magnum.melonds.R
import me.magnum.melonds.ui.layouteditor.LayoutComponentViewBuilder

class ButtonsLayoutComponentViewBuilder : LayoutComponentViewBuilder() {
    override fun build(context: Context): View {
        return ImageView(context).apply {
            setImageResource(R.drawable.buttons)
            background = ContextCompat.getDrawable(context, R.drawable.background_uiview)
        }
    }

    override fun getAspectRatio() = 1f
}