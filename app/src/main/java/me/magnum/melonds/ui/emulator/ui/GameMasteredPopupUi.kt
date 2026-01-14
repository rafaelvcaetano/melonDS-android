package me.magnum.melonds.ui.emulator.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import me.magnum.melonds.R
import me.magnum.melonds.ui.emulator.model.RAEventUi
import me.magnum.melonds.ui.theme.MelonTheme
import me.magnum.melonds.ui.theme.gameMasteryColor
import java.net.URL
import kotlin.time.Duration.Companion.seconds

private enum class MasteryPopupState {
    SHOW_ICON,
    SHOW_FULL,
}

@Composable
fun GameMasteredPopupUi(
    modifier: Modifier = Modifier,
    masteryEvent: RAEventUi.GameMastered,
) {
    var popupState by remember(masteryEvent) {
        mutableStateOf(MasteryPopupState.SHOW_ICON)
    }

    LaunchedEffect(masteryEvent) {
        delay(500)
        popupState = MasteryPopupState.SHOW_FULL
    }

    Card(
        modifier = modifier
            .padding(16.dp)
            .shadow(8.dp, RoundedCornerShape(8.dp), spotColor = gameMasteryColor)
            .widthIn(max = 400.dp),
        backgroundColor = gameMasteryColor,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(8.dp).height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (LocalInspectionMode.current) {
                Box(Modifier.size(40.dp).background(Color.Gray))
            } else {
                AsyncImage(
                    modifier = Modifier.size(40.dp),
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(masteryEvent.gameIcon.toString())
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                )
            }

            AnimatedContent(
                modifier = Modifier.fillMaxHeight(),
                targetState = popupState,
                transitionSpec = {
                    expandHorizontally(expandFrom = Alignment.Start) togetherWith ExitTransition.None
                },
                label = "content-animation",
            ) {
                when (it) {
                    MasteryPopupState.SHOW_ICON -> {
                        Box(Modifier.fillMaxHeight())
                    }
                    MasteryPopupState.SHOW_FULL -> {
                        val resources = LocalContext.current.resources
                        val masteryText = if (masteryEvent.forHardcodeMode) {
                            stringResource(R.string.game_mastered, masteryEvent.gameTitle)
                        } else {
                            stringResource(R.string.game_completed, masteryEvent.gameTitle)
                        }
                        val romPlayTime = remember(masteryEvent.playTime) {
                            masteryEvent.playTime?.toComponents { hours, minutes, _, _ ->
                                if (hours > 0) {
                                    resources.getString(R.string.info_play_time_hours_minutes, hours, minutes)
                                } else {
                                    resources.getString(R.string.info_play_time_minutes, minutes)
                                }
                            }
                        }
                        val userInfo = remember(masteryEvent) {
                            buildString {
                                if (masteryEvent.userName != null) {
                                    append(masteryEvent.userName)
                                }

                                if (masteryEvent.userName != null && romPlayTime != null) {
                                    append(" • ")
                                }

                                if (romPlayTime != null) {
                                    append(romPlayTime)
                                }
                            }
                        }

                        Column(Modifier.padding(start = 8.dp)) {
                            Text(
                                text = masteryText,
                                style = MaterialTheme.typography.body2,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                            if (userInfo.isNotEmpty()) {
                                Text(
                                    text = userInfo,
                                    style = MaterialTheme.typography.body2,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewGameMasteredPopupUi() {
    MelonTheme {
        GameMasteredPopupUi(
            masteryEvent = RAEventUi.GameMastered(
                gameTitle = "Mega Robots: Master Clankers",
                gameIcon = URL("http://example.com/icon.png"),
                userName = "UserName",
                playTime = 92.seconds,
                forHardcodeMode = false,
            )
        )
    }
}