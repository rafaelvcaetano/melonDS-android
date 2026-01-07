package me.magnum.melonds.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

@JvmInline
value class PaddingSides(val value: Int) {

    infix fun or(other: PaddingSides): PaddingSides {
        return PaddingSides(this.value or other.value)
    }

    infix fun and(other: PaddingSides): PaddingSides {
        return PaddingSides(this.value and other.value)
    }

    companion object {
        val Start = PaddingSides(0b1000)
        val Top = PaddingSides(0b0100)
        val End = PaddingSides(0b0010)
        val Bottom = PaddingSides(0b0001)
        val Vertical = Top or Bottom
        val Horizontal = Start or End
        val All = Vertical or Horizontal
    }
}

@Composable
fun PaddingValues.only(sides: PaddingSides): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = if ((sides and PaddingSides.Start).value != 0) calculateStartPadding(layoutDirection) else 0.dp,
        top = if ((sides and PaddingSides.Top).value != 0) calculateTopPadding() else 0.dp,
        end = if ((sides and PaddingSides.End).value != 0) calculateEndPadding(layoutDirection) else 0.dp,
        bottom = if ((sides and PaddingSides.Bottom).value != 0) calculateBottomPadding() else 0.dp,
    )
}