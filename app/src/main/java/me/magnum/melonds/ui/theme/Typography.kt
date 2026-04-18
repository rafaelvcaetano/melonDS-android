package me.magnum.melonds.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val MelonTypography @Composable get() = Typography(
    body1 = MaterialTheme.typography.body1.copy(lineHeight = 20.sp),
    button = MaterialTheme.typography.button.copy(fontWeight = FontWeight.Bold),
)