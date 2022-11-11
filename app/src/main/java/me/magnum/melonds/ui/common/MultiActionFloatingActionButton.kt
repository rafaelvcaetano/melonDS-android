package me.magnum.melonds.ui.common

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

data class FabActionItem(val id: Int, val title: String, val icon: Painter)

private enum class FabState {
    EXPANDED,
    COLLAPSED,
}

@Composable
fun MultiActionFloatingActionButton(
    modifier: Modifier,
    actions: List<FabActionItem>,
    onActionClicked: (FabActionItem) -> Unit,
    content: @Composable () -> Unit,
) {
    val state = remember { mutableStateOf(FabState.COLLAPSED) }

    Column(modifier, horizontalAlignment = Alignment.End) {
        actions.forEachIndexed { index, item ->
            AnimatedVisibility(
                visible = state.value == FabState.EXPANDED,
                enter = slideInVertically(initialOffsetY = { it * (actions.size - index) }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it * (actions.size - index) }) + fadeOut(),
            ) {
                Row(
                    modifier = Modifier.padding(bottom = 18.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        elevation = 4.dp,
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            text = item.title,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    FloatingActionButton(
                        modifier = Modifier.size(40.dp),
                        onClick = {
                            state.value = FabState.COLLAPSED
                            onActionClicked(item)
                        },
                    ) {
                        Icon(
                            painter = item.icon,
                            contentDescription = null,
                        )
                    }
                }
            }
        }

        val rotation = animateFloatAsState(targetValue = if (state.value == FabState.COLLAPSED) 0f else 45f)
        FloatingActionButton(
            modifier = Modifier.rotate(rotation.value),
            onClick = {
                state.value = if (state.value == FabState.COLLAPSED) {
                    FabState.EXPANDED
                } else {
                    FabState.COLLAPSED
                }
            },
            content = content,
        )
    }
}