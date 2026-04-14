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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.delay
import me.magnum.melonds.R
import me.magnum.melonds.ui.emulator.ui.AchievementInfo
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun ServerCommunicationFailedUi(errorInfo: AchievementInfo.ServerCommunicationFailed) {
    val alphaTransition = remember(errorInfo.source) { Animatable(1f) }
    var isDescriptionVisible by remember(errorInfo.source) { mutableStateOf(false) }

    AchievementInfoUi(
        modifier = Modifier.padding(8.dp).graphicsLayer {
            alpha = alphaTransition.value
        },
        iconData = ResourcesCompat.getDrawable(LocalResources.current, R.drawable.ic_ra_error, null)!!,
        state = errorInfo.state,
    ) {
        LaunchedEffect(errorInfo.source) {
            delay(500.milliseconds)
            isDescriptionVisible = true
            delay(3.seconds)
            isDescriptionVisible = false
            alphaTransition.animateTo(0.5f)
        }

        AnimatedVisibility(isDescriptionVisible) {
            Column(Modifier.padding(start = 4.dp)) {
                val errorMessage = when (errorInfo.source) {
                    is AchievementInfo.ServerCommunicationFailed.ErrorSource.AwardAchievement -> "Error unlocking achievement"
                    is AchievementInfo.ServerCommunicationFailed.ErrorSource.SubmitLeaderboard -> "Error submitting leaderboard entry"
                }

                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = "Keep playing while we retry in the background",
                    style = MaterialTheme.typography.caption,
                )
            }
        }
    }
}