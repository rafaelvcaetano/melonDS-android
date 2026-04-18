package me.magnum.melonds.ui.common.preference

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SeekBarItem(
    name: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit,
    horizontalPadding: Dp = 16.dp,
) {
    var currentValue by remember(value) { mutableFloatStateOf(value) }
    val jumpStep = remember(range) {
        (range.endInclusive - range.start) / 20f
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = { })
            .onKeyEvent {
                if (it.type == KeyEventType.KeyDown) {
                    when (it.key) {
                        Key.Minus, Key.DirectionLeft -> {
                            currentValue = (currentValue - jumpStep).coerceAtLeast(range.start)
                            true
                        }
                        Key.Plus, Key.DirectionRight -> {
                            currentValue = (currentValue + jumpStep).coerceAtMost(range.endInclusive)
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .heightIn(min = 64.dp)
            .padding(start = horizontalPadding, end = horizontalPadding, top = 8.dp, bottom = 8.dp),
    ) {
        CompositionLocalProvider(LocalContentAlpha provides if (enabled) ContentAlpha.high else ContentAlpha.disabled) {
            Text(
                text = name,
                style = MaterialTheme.typography.body1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Slider(
                modifier = Modifier.fillMaxWidth().focusable(false),
                value = currentValue,
                valueRange = range,
                onValueChange = { currentValue = it },
                onValueChangeFinished = { onValueChange(currentValue) },
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colors.secondary,
                    activeTrackColor = MaterialTheme.colors.secondary,
                ),
            )
        }
    }
}