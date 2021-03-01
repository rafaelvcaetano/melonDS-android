package me.magnum.melonds.utils

import androidx.annotation.DrawableRes
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.LayoutComponent

fun getInputName(input: Input): Int {
    return when (input) {
        Input.A -> R.string.input_a
        Input.B -> R.string.input_b
        Input.X -> R.string.input_x
        Input.Y -> R.string.input_y
        Input.LEFT -> R.string.input_left
        Input.RIGHT -> R.string.input_right
        Input.UP -> R.string.input_up
        Input.DOWN -> R.string.input_down
        Input.L -> R.string.input_l
        Input.R -> R.string.input_r
        Input.START -> R.string.input_start
        Input.SELECT -> R.string.input_select
        Input.HINGE -> R.string.input_lid
        Input.PAUSE -> R.string.input_pause
        Input.FAST_FORWARD -> R.string.input_fast_forward
        Input.TOGGLE_SOFT_INPUT -> R.string.input_toggle_soft_input
        Input.RESET -> R.string.reset
        else -> -1
    }
}

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
        else -> -1
    }
}

@DrawableRes
fun getInputDrawable(input: Input): Int {
    return when (input) {
        Input.L -> R.drawable.button_l
        Input.R -> R.drawable.button_r
        Input.START -> R.drawable.button_start
        Input.SELECT -> R.drawable.button_select
        Input.FAST_FORWARD -> R.drawable.button_fast_forward
        Input.HINGE -> R.drawable.button_toggle_lid
        Input.TOGGLE_SOFT_INPUT -> R.drawable.ic_touch_enabled
        Input.RESET -> R.drawable.ic_refresh
        else -> -1
    }
}