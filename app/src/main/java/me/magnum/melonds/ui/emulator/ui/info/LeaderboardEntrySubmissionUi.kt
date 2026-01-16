package me.magnum.melonds.ui.emulator.ui.info

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.magnum.melonds.R
import me.magnum.melonds.ui.emulator.ui.AchievementInfo
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun LeaderboardEntrySubmissionUi(
    info: AchievementInfo.LeaderboardEntrySubmitted,
) {
    AchievementInfoUi(
        modifier = Modifier.padding(8.dp),
        icon = info.gameIcon,
        state = info.state,
    ) {
        var isDescriptionVisible by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            delay(500.milliseconds)
            isDescriptionVisible = true
            delay(4.seconds)
            info.state.dismiss()
        }

        AnimatedVisibility(isDescriptionVisible) {
            Column(Modifier.padding(start = 4.dp)) {
                Text(
                    text = info.title,
                    style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = stringResource(R.string.leaderboard_submission_info, info.formattedScore, info.rank, info.numberOfEntries),
                    style = MaterialTheme.typography.caption,
                )
            }
        }
    }
}
