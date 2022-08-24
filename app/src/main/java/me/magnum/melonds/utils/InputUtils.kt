package me.magnum.melonds.utils

import me.magnum.melonds.R
import me.magnum.melonds.domain.model.LayoutComponent

fun getLayoutComponentName(layoutComponent: LayoutComponent): Int {
    return when (layoutComponent) {
        LayoutComponent.TOP_SCREEN -> R.string.top_screen
        LayoutComponent.BOTTOM_SCREEN -> R.string.bottom_screen
        LayoutComponent.DPAD -> R.string.input_dpad
        LayoutComponent.BUTTONS -> R.string.input_abxy_buttons
        LayoutComponent.BUTTON_L -> R.string.input_l
        LayoutComponent.BUTTON_R -> R.string.input_r
        LayoutComponent.BUTTON_START -> R.string.input_start
        LayoutComponent.BUTTON_SELECT -> R.string.input_select
        LayoutComponent.BUTTON_HINGE -> R.string.input_lid
        LayoutComponent.BUTTON_PAUSE -> R.string.input_pause
        LayoutComponent.BUTTON_FAST_FORWARD_TOGGLE -> R.string.input_fast_forward
        LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT -> R.string.input_toggle_soft_input
        LayoutComponent.BUTTON_RESET -> R.string.reset
        LayoutComponent.BUTTON_SWAP_SCREENS -> R.string.input_swap_screens
        LayoutComponent.BUTTON_QUICK_SAVE -> R.string.input_quick_save
        LayoutComponent.BUTTON_QUICK_LOAD -> R.string.input_quick_load
        LayoutComponent.BUTTON_REWIND -> R.string.rewind
    }
}