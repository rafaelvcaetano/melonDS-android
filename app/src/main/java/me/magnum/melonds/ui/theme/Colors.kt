package me.magnum.melonds.ui.theme

import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import me.magnum.melonds.R

val LightMelonColors @Composable get() = lightColors(
    primary = colorResource(id = R.color.colorPrimary),
    primaryVariant = colorResource(id = R.color.colorPrimaryDark),
    secondary = colorResource(id = R.color.colorAccent),
    onSecondary = Color.White,
    onSurface = colorResource(id = R.color.romConfigButtonDefault),
)

val DarkMelonColors @Composable get() = darkColors(
    primary = colorResource(id = R.color.colorPrimary),
    primaryVariant = colorResource(id = R.color.colorPrimaryDark),
    secondary = colorResource(id = R.color.colorAccent),
    onSecondary = Color.White,
    onSurface = colorResource(id = R.color.romConfigButtonDefault),
)