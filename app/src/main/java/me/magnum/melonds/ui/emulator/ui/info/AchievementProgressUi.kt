package me.magnum.melonds.ui.emulator.ui.info

import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.magnum.melonds.ui.emulator.ui.AchievementInfo.AchievementProgress
import me.magnum.rcheevosapi.model.RAAchievement
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun AchievementProgressUi(progressInfo: AchievementProgress) {
    LaunchedEffect(progressInfo) {
        delay(2.seconds)
        progressInfo.state.dismiss()
    }

    AchievementInfoUi(
        modifier = Modifier.padding(8.dp),
        icon = progressInfo.achievement.badgeUrlUnlocked,
        state = progressInfo.state,
    ) {
        Text(
            modifier = Modifier.padding(start = 4.dp),
            text = progressInfo.progress,
            style = MaterialTheme.typography.body1,
        )
    }
}
