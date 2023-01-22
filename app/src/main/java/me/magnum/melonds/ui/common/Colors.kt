package me.magnum.melonds.ui.common

import androidx.compose.material.SwitchColors
import androidx.compose.material.SwitchDefaults
import androidx.compose.runtime.Composable
import me.magnum.melonds.ui.theme.uncheckedThumbColor

@Composable
fun melonSwitchColors(): SwitchColors {
    return SwitchDefaults.colors(
        uncheckedThumbColor = uncheckedThumbColor,
    )
}