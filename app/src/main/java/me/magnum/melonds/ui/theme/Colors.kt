package me.magnum.melonds.ui.theme

import androidx.compose.material.Colors
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import me.magnum.melonds.R

@Suppress("unused")
val Colors.toolbarBackground: Color @Composable get() = colorResource(id = R.color.toolbarBackground)

val uncheckedThumbColor: Color @Composable get() = colorResource(id = R.color.switchThumbUnselected)

val LightMelonColors @Composable get() = lightColors(
    primary = Color(0xFFF44336),          // R.color.colorPrimary
    primaryVariant = Color(0xFFD32F2F),   // R.color.colorPrimaryDark
    secondary = Color(0xFF5C913B),        // R.color.colorAccent
    secondaryVariant = Color(0xFF5C913B), // R.color.colorAccent
    background = Color(0xFFFFFFFF),       // R.color.colorBackground
    surface = Color(0xFFFAFAFA),          // R.color.colorSurface
    onPrimary = Color(0xFFFFFFFF),        // R.color.colorOnSecondary
    onSecondary = Color(0xFFFFFFFF),      // R.color.colorOnSecondary
    onSurface = Color(0xFF222222),        // R.color.textColorPrimary
    onBackground = Color(0xFF767676),     // R.color.textColorSecondary
)

val DarkMelonColors @Composable get() = darkColors(
    primary = Color(0xFF333333),          // R.color.colorPrimary,
    primaryVariant = Color(0xFF222222),   // R.color.colorPrimaryDark,
    secondary = Color(0xFFF44336),        // R.color.colorAccent,
    secondaryVariant = Color(0xFFF44336), // R.color.colorAccent,
    background = Color(0xFF000000),       // R.color.colorBackground,
    surface = Color(0xFF303030),          // R.color.colorSurface,
    onPrimary = Color(0xFFFFFFFF),        // R.color.colorOnSecondary,
    onSecondary = Color(0xFFFFFFFF),      // R.color.colorOnSecondary,
    onSurface = Color(0xFFFFFFFF),        // R.color.textColorPrimary,
    onBackground = Color(0xFFC1C1C1),     // R.color.textColorSecondary,
)