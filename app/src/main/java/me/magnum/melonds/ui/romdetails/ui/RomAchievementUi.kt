package me.magnum.melonds.ui.romdetails.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component1
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component2
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.retroachievements.RARuntimeUserAchievement
import me.magnum.melonds.domain.model.retroachievements.RAUserAchievement
import me.magnum.melonds.ui.common.MelonPreviewSet
import me.magnum.melonds.ui.common.achievements.ui.model.AchievementUiModel
import me.magnum.melonds.ui.common.melonTextButtonColors
import me.magnum.melonds.ui.romdetails.ui.preview.mockRAAchievementPreview
import me.magnum.melonds.ui.theme.MelonTheme

@Composable
fun RomAchievementUi(
    modifier: Modifier,
    achievementModel: AchievementUiModel,
    onViewAchievement: () -> Unit,
    badgeSize: Dp = 52.dp,
) {
    val (bodyFocusRequester, linkFocusRequester) = remember { FocusRequester.createRefs() }
    var expanded by remember(achievementModel) {
        mutableStateOf(false)
    }

    val (achievement, isUnlocked) = when (achievementModel) {
        is AchievementUiModel.RuntimeAchievementUiModel -> achievementModel.runtimeAchievement.userAchievement.achievement to achievementModel.runtimeAchievement.userAchievement.isUnlocked
        is AchievementUiModel.UserAchievementUiModel -> achievementModel.userAchievement.achievement to achievementModel.userAchievement.isUnlocked
        is AchievementUiModel.PrimedAchievementUiModel -> achievementModel.achievement to true
    }

    Column(
        modifier = modifier
            .focusRequester(bodyFocusRequester)
            .focusProperties {
                end = if (expanded) linkFocusRequester else FocusRequester.Default
            }
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize()
    ) {
        Row(Modifier.fillMaxWidth()) {
            val image = if (isUnlocked) {
                achievement.badgeUrlUnlocked
            } else {
                achievement.badgeUrlLocked
            }

            if (LocalInspectionMode.current) {
                Box(
                    Modifier
                        .size(badgeSize)
                        .background(Color.Gray)
                )
            } else {
                AsyncImage(
                    modifier = Modifier.size(badgeSize),
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
                        append(achievement.getCleanTitle())
                        if (achievement.isMissable()) {
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
                    text = achievement.description,
                    style = MaterialTheme.typography.caption,
                    maxLines = descriptionMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )

                if (achievementModel is AchievementUiModel.RuntimeAchievementUiModel && achievementModel.hasProgress()) {
                    Spacer(Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            modifier = Modifier.padding(end = 8.dp),
                            text = stringResource(R.string.achievement_progress, achievementModel.runtimeAchievement.progress, achievementModel.runtimeAchievement.target),
                            style = MaterialTheme.typography.caption,
                        )

                        val achievementProgress = achievementModel.runtimeAchievement.relativeProgress()
                        LinearProgressIndicator(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(50)),
                            progress = achievementProgress,
                            color = MaterialTheme.colors.secondary,
                        )
                    }
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = buildAnnotatedString {
                        append(achievement.points.toString())
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

            if (achievement.isMissable()) {
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
                modifier = Modifier.align(Alignment.End)
                    .focusRequester(linkFocusRequester)
                    .focusProperties {
                        start = bodyFocusRequester
                    },
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
            achievementModel = AchievementUiModel.UserAchievementUiModel(
                userAchievement = RAUserAchievement(
                    achievement = mockRAAchievementPreview(),
                    isUnlocked = true,
                    forHardcoreMode = false,
                ),
            ),
            onViewAchievement = { },
        )
    }
}

@MelonPreviewSet
@Composable
fun PreviewRuntimeRomAchievementUi() {
    MelonTheme {
        RomAchievementUi(
            modifier = Modifier.fillMaxWidth(),
            achievementModel = AchievementUiModel.RuntimeAchievementUiModel(
                runtimeAchievement = RARuntimeUserAchievement(
                    userAchievement = RAUserAchievement(
                        achievement = mockRAAchievementPreview(),
                        isUnlocked = true,
                        forHardcoreMode = false,
                    ),
                    progress = 13,
                    target = 47,
                ),
            ),
            onViewAchievement = { },
        )
    }
}