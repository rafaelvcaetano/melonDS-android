package me.magnum.melonds.ui.common.componentbuilders

import android.content.Context
import android.view.View
import androidx.core.content.ContextCompat
import me.magnum.melonds.R
import me.magnum.melonds.ui.common.LayoutComponentViewBuilder

class EditorBackgroundLayoutComponentViewBuilder(private val baseBuilder: LayoutComponentViewBuilder) : LayoutComponentViewBuilder() {
    override fun build(context: Context): View {
        return baseBuilder.build(context).apply {
            background = ContextCompat.getDrawable(context, R.drawable.background_uiview_selector)
        }
    }

    override fun getAspectRatio() = baseBuilder.getAspectRatio()
}