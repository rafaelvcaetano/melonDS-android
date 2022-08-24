package me.magnum.melonds.ui.common.componentbuilders

import android.content.Context
import android.view.View
import android.widget.ImageView
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.LayoutComponent
import me.magnum.melonds.ui.common.LayoutComponentViewBuilder

class SingleButtonLayoutComponentViewBuilder(private val layoutComponent: LayoutComponent) : LayoutComponentViewBuilder() {
    override fun build(context: Context): View {
        return ImageView(context).apply {
            setImageResource(getInputDrawable())
        }
    }

    override fun getAspectRatio() = 1f

    private fun getInputDrawable(): Int {
        return when (layoutComponent) {
            LayoutComponent.BUTTON_L -> R.drawable.button_l
            LayoutComponent.BUTTON_R -> R.drawable.button_r
            LayoutComponent.BUTTON_START -> R.drawable.button_start
            LayoutComponent.BUTTON_SELECT -> R.drawable.button_select
            LayoutComponent.BUTTON_HINGE -> R.drawable.button_toggle_lid
            LayoutComponent.BUTTON_PAUSE -> R.drawable.button_pause
            LayoutComponent.BUTTON_FAST_FORWARD_TOGGLE -> R.drawable.button_fast_forward
            LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT -> R.drawable.ic_touch_enabled
            LayoutComponent.BUTTON_RESET -> R.drawable.button_reset
            LayoutComponent.BUTTON_SWAP_SCREENS -> R.drawable.button_swap_screens
            LayoutComponent.BUTTON_QUICK_SAVE -> R.drawable.button_quick_save
            LayoutComponent.BUTTON_QUICK_LOAD -> R.drawable.button_quick_load
            LayoutComponent.BUTTON_REWIND -> R.drawable.button_rewind
            else -> -1
        }
    }
}