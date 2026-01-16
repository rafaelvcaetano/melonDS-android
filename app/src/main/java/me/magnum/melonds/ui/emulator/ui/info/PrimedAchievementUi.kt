package me.magnum.melonds.ui.emulator.ui.info

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.magnum.melonds.R
import me.magnum.melonds.ui.common.MelonPreviewSet
import me.magnum.melonds.ui.emulator.ui.AchievementInfo.AchievementPrimed
import me.magnum.melonds.ui.romdetails.ui.preview.mockRAAchievementPreview
import me.magnum.melonds.ui.theme.MelonTheme
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun PrimedAchievementUi(primedInfo: AchievementPrimed) {
    val alphaTransition = remember {
        Animatable(1f)
    }

    AchievementInfoUi(
        modifier = Modifier.padding(8.dp).graphicsLayer {
            alpha = alphaTransition.value
        },
        icon = primedInfo.achievement.badgeUrlUnlocked,
        state = primedInfo.state,
    ) {
        var isDescriptionVisible by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            delay(500.milliseconds)
            isDescriptionVisible = true
            delay(3.seconds)
            isDescriptionVisible = false
            alphaTransition.animateTo(0.5f)
        }

        AnimatedVisibility(isDescriptionVisible) {
            Column(Modifier.padding(start = 4.dp)) {
                Text(
                    text = stringResource(R.string.challenge_started),
                    style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = primedInfo.achievement.description,
                    style = MaterialTheme.typography.caption,
                )
            }
        }
    }
}

@MelonPreviewSet
@Composable
private fun PreviewPrimedAchievementUi() {
    MelonTheme {
        PrimedAchievementUi(
            AchievementPrimed(
                achievement = mockRAAchievementPreview(description = "Do the thing without taking damage"),
                state = AchievementInfoState { },
            )
        )
    }
}
