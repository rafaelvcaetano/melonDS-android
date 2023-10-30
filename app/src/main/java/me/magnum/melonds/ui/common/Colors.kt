package me.magnum.melonds.ui.common

import androidx.compose.material.*
import androidx.compose.material.ExposedDropdownMenuDefaults.outlinedTextFieldColors
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

@Composable
fun melonTextButtonColors(): ButtonColors {
    return ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colors.secondary)
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun melonOutlinedTextFieldColors(): TextFieldColors {
    return outlinedTextFieldColors(
        cursorColor = MaterialTheme.colors.secondary,
        focusedBorderColor = MaterialTheme.colors.secondary.copy(alpha = ContentAlpha.high),
        focusedLabelColor = MaterialTheme.colors.secondary.copy(alpha = ContentAlpha.high),
        focusedTrailingIconColor = MaterialTheme.colors.secondary.copy(alpha = ContentAlpha.high),
    )
}