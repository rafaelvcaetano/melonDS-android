package me.magnum.melonds.ui.common

import androidx.compose.material.*
import androidx.compose.runtime.Composable
import me.magnum.melonds.ui.theme.uncheckedThumbColor

@Composable
fun melonSwitchColors(): SwitchColors {
    return SwitchDefaults.colors(
        uncheckedThumbColor = uncheckedThumbColor,
    )
}

@Composable
fun melonButtonColors(): ButtonColors {
    return ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.secondary, contentColor = MaterialTheme.colors.onSecondary)
}