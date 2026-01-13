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
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.Icon
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
import me.magnum.melonds.ui.common.MelonPreviewSet
import me.magnum.melonds.ui.common.melonTextButtonColors
import me.magnum.melonds.ui.romdetails.ui.preview.mockRAAchievementPreview
import me.magnum.melonds.ui.theme.MelonTheme
import me.magnum.rcheevosapi.model.RAAchievement

@Composable
fun RomAchievementUi(
    modifier: Modifier,
    achievement: RAAchievement,
    showLocked: Boolean,
    onViewAchievement: () -> Unit,
    badgeSize: Dp = 52.dp,
) {
    val (bodyFocusRequester, linkFocusRequester) = remember { FocusRequester.createRefs() }
    var expanded by remember(achievement) {
        mutableStateOf(false)
    }

    Column(
        modifier = modifier
            .focusRequester(bodyFocusRequester)
            .focusProperties {
                end = if (expanded) linkFocusRequester else FocusRequester.Default
            }
            .clickable { expanded = !expanded }
            .padding(8.dp)
            .animateContentSize()
    ) {
        Row(Modifier.fillMaxWidth()) {
            val image = if (showLocked) {
                achievement.badgeUrlLocked
            } else {
                achievement.badgeUrlUnlocked
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
            achievement = mockRAAchievementPreview(),
            showLocked = false,
            onViewAchievement = {},
        )
    }
}