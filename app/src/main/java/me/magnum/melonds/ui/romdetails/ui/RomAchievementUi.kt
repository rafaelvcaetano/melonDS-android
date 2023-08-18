package me.magnum.melonds.ui.romdetails.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
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
import me.magnum.melonds.ui.common.melonTextButtonColors
import me.magnum.melonds.ui.romdetails.ui.preview.mockRAAchievementPreview
import me.magnum.melonds.ui.theme.MelonTheme

@Composable
fun RomAchievementUi(
    modifier: Modifier,
    userAchievement: RAUserAchievement,
    onViewAchievement: () -> Unit,
) {
    var expanded by remember(userAchievement) {
        mutableStateOf(false)
    }

    Column(
        modifier = modifier
            .clickable { expanded = !expanded }
            .padding(8.dp)
            .animateContentSize()
    ) {
        Row(Modifier.fillMaxWidth()) {
            val image = if (userAchievement.isUnlocked) {
                userAchievement.achievement.badgeUrlUnlocked
            } else {
                userAchievement.achievement.badgeUrlLocked
            }

            if (LocalInspectionMode.current) {
                Box(
                    Modifier
                        .size(52.dp)
                        .background(Color.Gray))
            } else {
                AsyncImage(
                    modifier = Modifier.size(52.dp),
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(image.toString())
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                )
            }

            Spacer(Modifier.width(4.dp))

            Column(modifier = Modifier.weight(1f)) {
                val nameMaxLines = if (expanded) Int.MAX_VALUE else 1
                Text(
                    text = buildAnnotatedString {
                        append(userAchievement.achievement.getCleanTitle())
                        if (userAchievement.achievement.isMissable()) {
                            append(' ')
                            appendInlineContent(id = "icon-missable", alternateText = stringResource(id = R.string.achievement_missable))
                        }
                    },
                    inlineContent = mapOf(
                        "icon-missable" to InlineTextContent(Placeholder(MaterialTheme.typography.body1.fontSize, MaterialTheme.typography.body1.fontSize, PlaceholderVerticalAlign.Center)) {
                            Icon(
                                modifier = Modifier.fillMaxSize(),
                                painter = painterResource(id = R.drawable.ic_status_warn),
                                tint = MaterialTheme.typography.body1.color,
                                contentDescription = null,
                            )
                        }
                    ),
                    style = MaterialTheme.typography.body1,
                    maxLines = nameMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )

                val descriptionMaxLines = if (expanded) Int.MAX_VALUE else 2
                Text(
                    text = userAchievement.achievement.description,
                    style = MaterialTheme.typography.caption,
                    maxLines = descriptionMaxLines,
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

        if (expanded) {
            Spacer(Modifier.height(8.dp))

            if (userAchievement.achievement.isMissable()) {
                Text(
                    modifier = Modifier.align(Alignment.End),
                    text = buildAnnotatedString {
                        appendInlineContent(id = "icon-missable", alternateText = stringResource(id = R.string.achievement_missable))
                        append(' ')
                        append(stringResource(id = R.string.achievement_missable_description))
                    },
                    inlineContent = mapOf(
                        "icon-missable" to InlineTextContent(Placeholder(MaterialTheme.typography.caption.fontSize, MaterialTheme.typography.caption.fontSize, PlaceholderVerticalAlign.Center)) {
                            Icon(
                                modifier = Modifier.fillMaxSize(),
                                painter = painterResource(id = R.drawable.ic_status_warn),
                                tint = MaterialTheme.typography.caption.color,
                                contentDescription = null,
                            )
                        }
                    ),
                    style = MaterialTheme.typography.caption,
                )
            }

            TextButton(
                modifier = Modifier.align(Alignment.End),
                onClick = onViewAchievement,
                colors = melonTextButtonColors(),
            ) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_link),
                    contentDescription = null,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(id = R.string.view_achievement).uppercase(),
                    style = MaterialTheme.typography.button,
                )
            }
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
                achievement = mockRAAchievementPreview(),
                isUnlocked = true,
                forHardcoreMode = false,
            ),
            onViewAchievement = {},
        )
    }
}