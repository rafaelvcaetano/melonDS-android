package me.magnum.melonds.ui.romdetails.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.retroachievements.RAUserAchievement
import me.magnum.melonds.ui.common.MelonPreviewSet
import me.magnum.melonds.ui.theme.MelonTheme
import me.magnum.rcheevosapi.model.RAAchievement
import me.magnum.rcheevosapi.model.RAGameId
import java.net.URL

@Composable
fun RomAchievementUi(
    modifier: Modifier,
    userAchievement: RAUserAchievement,
) {
    Row(
        modifier = modifier.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val image = if (userAchievement.isUnlocked) {
            userAchievement.achievement.badgeUrlUnlocked
        } else {
            userAchievement.achievement.badgeUrlLocked
        }

        if (LocalInspectionMode.current) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(Color.Gray))
        } else {
            AsyncImage(
                modifier = Modifier.size(48.dp),
                model = ImageRequest.Builder(LocalContext.current)
                    .data(image.toString())
                    .crossfade(true)
                    .build(),
                contentDescription = null,
            )
        }

        Spacer(Modifier.width(4.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = userAchievement.achievement.title,
                style = MaterialTheme.typography.body1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = userAchievement.achievement.description,
                style = MaterialTheme.typography.caption,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = buildAnnotatedString {
                    append(userAchievement.achievement.points.toString())
                    appendInlineContent(id = "icon-points", alternateText = stringResource(id = R.string.points))
                },
                inlineContent = mapOf(
                    "icon-points" to InlineTextContent(Placeholder(MaterialTheme.typography.body1.fontSize, MaterialTheme.typography.body1.fontSize, PlaceholderVerticalAlign.Center)) {
                        Image(
                            modifier = Modifier.fillMaxSize(),
                            painter = painterResource(id = R.drawable.ic_points),
                            contentDescription = null,
                        )
                    }
                ),
                fontWeight = FontWeight.Bold
            )
            Text(text = stringResource(id = R.string.points_abbreviated), style = MaterialTheme.typography.caption)
        }
    }
}

@MelonPreviewSet
@Composable
fun PreviewRomAchievementUi() {
    MelonTheme {
        RomAchievementUi(
            modifier = Modifier.fillMaxWidth(),
            userAchievement = RAUserAchievement(
                achievement = RAAchievement(
                    id = 123,
                    gameId = RAGameId(123),
                    totalAwardsCasual = 5435,
                    totalAwardsHardcore = 4532,
                    title = "Amazing Achievement",
                    description = "Do the definitely amazing stuff while back-flipping on top of a turtle.",
                    points = 10,
                    displayOrder = 0,
                    badgeUrlUnlocked = URL(""),
                    badgeUrlLocked = URL(""),
                    memoryAddress = "",
                    type = RAAchievement.Type.CORE,
                ),
                isUnlocked = true,
            )
        )
    }
}