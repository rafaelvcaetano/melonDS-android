package me.magnum.melonds.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight

val MelonTypography @Composable get() = Typography(
    button = MaterialTheme.typography.button.copy(fontWeight = FontWeight.Bold),
)