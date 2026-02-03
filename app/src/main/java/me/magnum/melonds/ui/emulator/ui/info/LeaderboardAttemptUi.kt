package me.magnum.melonds.ui.emulator.ui.info

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.magnum.melonds.R
import me.magnum.melonds.ui.emulator.ui.AchievementInfo
import me.magnum.melonds.ui.theme.MelonTheme
import me.magnum.rcheevosapi.model.RAGameId
import me.magnum.rcheevosapi.model.RALeaderboard
import me.magnum.rcheevosapi.model.RASetId
import java.net.URL
import kotlin.time.Duration.Companion.seconds

private enum class LeaderboardAttemptState {
    SHOW_START_MESSAGE,
    SHOW_PROGRESS,
}

@Composable
internal fun LeaderboardAttemptUi(
    info: AchievementInfo.LeaderboardAttempt,
) {
    var attemptState by remember { mutableStateOf(LeaderboardAttemptState.SHOW_START_MESSAGE) }
    val alphaTransition = remember {
        Animatable(1f)
    }

    LaunchedEffect(Unit) {
        delay(3.seconds)
        attemptState = LeaderboardAttemptState.SHOW_PROGRESS
        alphaTransition.animateTo(0.5f)
    }

    AchievementInfoUi(
        modifier = Modifier.padding(8.dp).graphicsLayer { alpha = alphaTransition.value },
        icon = info.gameIcon,
        state = info.state,
    ) {
        AnimatedContent(
            targetState = attemptState,
            transitionSpec = {
                slideInVertically { it } togetherWith slideOutVertically { -it }
            },
            contentAlignment = Alignment.CenterStart,
            label = "leaderboard-attempt-content",
        ) { state ->
            when (state) {
                LeaderboardAttemptState.SHOW_START_MESSAGE -> {
                    Column(Modifier.padding(start = 4.dp)) {
                        Text(
                            text = stringResource(R.string.leaderboard_attempt_started),
                            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                        )
                        Text(
                            text = info.leaderboard.title,
                            style = MaterialTheme.typography.caption,
                            maxLines = 1,
                        )
                    }
                }
                LeaderboardAttemptState.SHOW_PROGRESS -> {
                    Text(
                        modifier = Modifier.padding(start = 4.dp),
                        text = info.currentValue,
                        style = MaterialTheme.typography.body1,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewLeaderboardAttemptUi() {
    MelonTheme {
        LeaderboardAttemptUi(
            info = AchievementInfo.LeaderboardAttempt(
                leaderboard = RALeaderboard(
                    id = 0,
                    gameId = RAGameId(0),
                    setId = RASetId(0),
                    mem = "",
                    format = "",
                    lowerIsBetter = false,
                    title = "Fastest Boy in the West",
                    description = "",
                    hidden = false
                ),
                gameIcon = URL("https://example.com/icon.png"),
                currentValue = "0:25.74",
                state = AchievementInfoState { },
            )
        )
    }
}
