package me.magnum.melonds.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import me.magnum.melonds.R

val MelonTypography @Composable get() = Typography(
    body1 = MaterialTheme.typography.body1.copy(fontFamily = FontFamily.SansSerif, color = colorResource(id = R.color.textColorPrimary)),
    body2 = MaterialTheme.typography.body2.copy(fontFamily = FontFamily.SansSerif, color = colorResource(id = R.color.textColorSecondary)),
    button = MaterialTheme.typography.button.copy(fontFamily = FontFamily.SansSerif, color = colorResource(id = R.color.colorOnSecondary), fontWeight = FontWeight.Bold),
)