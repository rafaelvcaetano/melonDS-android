package me.magnum.melonds.ui.emulator.ui.info

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.net.URL

@Composable
internal fun AchievementInfoUi(
    modifier: Modifier = Modifier,
    icon: URL,
    state: AchievementInfoState,
    body: (@Composable RowScope.() -> Unit)? = null,
) {
    LaunchedEffect(Unit) {
        // Check if it was dismissed before it was even shown
        if (state.dismissed) {
            state.notifyDismissed()
        } else {
            state.show()
        }
    }

    AnimatedVisibility(
        visibleState = state.visibility,
        enter = fadeIn() + slideInVertically() + expandVertically(clip = false, expandFrom = Alignment.CenterVertically),
        exit = fadeOut() + slideOutHorizontally(),
    ) {
        DisposableEffect(state) {
            onDispose {
                state.notifyDismissed()
            }
        }

        Card(
            modifier = modifier.shadow(4.dp, RoundedCornerShape(4.dp)),
            shape = RoundedCornerShape(4.dp),
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (LocalInspectionMode.current) {
                    Box(Modifier.size(32.dp).background(Color.Gray))
                } else {
                    AsyncImage(
                        modifier = Modifier.size(32.dp),
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(icon.toString())
                            .crossfade(false)
                            .build(),
                        contentDescription = null,
                    )
                }

                body?.invoke(this)
            }
        }
    }
}

internal class AchievementInfoState(private val onDismiss: () -> Unit) {
    val visibility = MutableTransitionState(false)
    var dismissed by mutableStateOf(false)
        private set

    fun show() {
        visibility.targetState = true
    }

    fun dismiss() {
        dismissed = true
        visibility.targetState = false
    }

    fun notifyDismissed() {
        onDismiss()
    }
}
