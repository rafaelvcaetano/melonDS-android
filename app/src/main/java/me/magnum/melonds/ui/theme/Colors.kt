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

val LightMelonColors @Composable get() = lightColors(
    primary = colorResource(id = R.color.colorPrimary),
    primaryVariant = colorResource(id = R.color.colorPrimaryDark),
    secondary = colorResource(id = R.color.colorAccent),
    surface = colorResource(id = R.color.colorSurface),
    onPrimary = colorResource(id = R.color.colorOnSecondary),
    onSecondary = colorResource(id = R.color.colorOnSecondary),
    onSurface = colorResource(id = R.color.textColorPrimary),
)

val DarkMelonColors @Composable get() = darkColors(
    primary = colorResource(id = R.color.colorPrimary),
    primaryVariant = colorResource(id = R.color.colorPrimaryDark),
    secondary = colorResource(id = R.color.colorAccent),
    surface = colorResource(id = R.color.colorSurface),
    onPrimary = colorResource(id = R.color.colorOnSecondary),
    onSecondary = colorResource(id = R.color.colorOnSecondary),
    onSurface = colorResource(id = R.color.textColorPrimary),
)