package me.magnum.melonds.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun MelonTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (isDarkTheme) DarkMelonColors else LightMelonColors

    MaterialTheme(
        colors = colors,
        typography = MelonTypography,
    ) {
        content()
    }
}