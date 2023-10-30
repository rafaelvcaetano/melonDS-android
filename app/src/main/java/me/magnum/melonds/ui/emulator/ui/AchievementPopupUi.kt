package me.magnum.melonds.ui.emulator.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import me.magnum.melonds.R
import me.magnum.melonds.ui.common.MelonPreviewSet
import me.magnum.melonds.ui.theme.MelonTheme
import me.magnum.rcheevosapi.model.RAAchievement
import me.magnum.rcheevosapi.model.RAGameId
import java.net.URL

private enum class PopupState {
    SHOW_ICON,
    SHOW_TITLE,
    SHOW_DESCRIPTION,
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AchievementPopupUi(
    modifier: Modifier,
    achievement: RAAchievement,
) {
    var popupState by remember(achievement) {
        mutableStateOf(PopupState.SHOW_ICON)
    }

    LaunchedEffect(achievement) {
        delay(500)
        popupState = PopupState.SHOW_TITLE
        delay(2000)
        popupState = PopupState.SHOW_DESCRIPTION
    }

    Card(
        modifier = modifier
            .padding(16.dp)
            .shadow(8.dp, RoundedCornerShape(8.dp))
            .widthIn(max = 400.dp),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                modifier = Modifier.size(40.dp),
                model = ImageRequest.Builder(LocalContext.current)
                    .data(achievement.badgeUrlUnlocked.toString())
                    .crossfade(false)
                    .build(),
                contentDescription = null,
            )

            AnimatedContent(
                targetState = popupState,
                transitionSpec = {
                    if (targetState == PopupState.SHOW_TITLE) {
                        expandIn { IntSize(0, it.height) } with fadeOut()
                    } else {
                        slideInVertically { it } + fadeIn() with slideOutVertically { -it } + fadeOut()
                    }
                }
            ) {
                when (it) {
                    PopupState.SHOW_ICON -> {
                        // No extra content is shown
                    }
                    PopupState.SHOW_TITLE -> {
                        Column {
                            Text(
                                text = stringResource(id = R.string.achievement_unlocked),
                                style = MaterialTheme.typography.body2,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                            Text(
                                text = achievement.getCleanTitle(),
                                style = MaterialTheme.typography.body2,
                                maxLines = 1,
                            )
                        }
                    }
                    PopupState.SHOW_DESCRIPTION -> {
                        Text(
                            text = achievement.description,
                            style = MaterialTheme.typography.body2,
                        )
                    }
                }
            }
        }
    }
}

@Composable
@MelonPreviewSet
private fun PreviewAchievementPopupUi() {
    MelonTheme {
        AchievementPopupUi(
            modifier = Modifier,
            achievement = RAAchievement(
                id = 0,
                gameId = RAGameId(0),
                totalAwardsCasual = 0,
                totalAwardsHardcore = 0,
                title = "Super Achievement",
                description = "Make something amazing happen, IDK. But this a very long and useless description.",
                points = 5,
                displayOrder = 0,
                badgeUrlLocked = URL("http://localhost:80"),
                badgeUrlUnlocked = URL("http://localhost:80"),
                memoryAddress = "",
                type = RAAchievement.Type.CORE,
            )
        )
    }
}