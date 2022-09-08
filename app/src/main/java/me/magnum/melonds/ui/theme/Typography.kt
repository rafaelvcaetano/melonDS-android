package me.magnum.melonds.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import me.magnum.melonds.R

val MelonTypography @Composable get() = Typography(
    body1 = MaterialTheme.typography.body1.copy(color = colorResource(id = R.color.textColorPrimary)),
    body2 = MaterialTheme.typography.body2.copy(color = colorResource(id = R.color.textColorSecondary)),
)