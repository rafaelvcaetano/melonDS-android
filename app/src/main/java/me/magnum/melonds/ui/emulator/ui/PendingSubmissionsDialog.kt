package me.magnum.melonds.ui.emulator.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Bullet
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import me.magnum.melonds.R
import me.magnum.melonds.ui.common.component.dialog.BaseDialog
import me.magnum.melonds.ui.common.component.dialog.DialogButton
import me.magnum.melonds.ui.emulator.component.RetroAchievementsSubmissionHandler
import me.magnum.melonds.ui.theme.MelonTheme
import kotlin.time.Duration.Companion.seconds

@Composable
fun PendingSubmissionsDialog(
    pendingSubmissionsSummaryFlow: Flow<RetroAchievementsSubmissionHandler.PendingSubmissionsSummary>,
    onExit: () -> Unit,
    onCancel: () -> Unit,
) {
    val pendingSubmissionsSummary by pendingSubmissionsSummaryFlow.collectAsStateWithLifecycle(null)
    val initialPendingSubmissionsSummary = remember(pendingSubmissionsSummary != null) { pendingSubmissionsSummary }
    var isExiting by remember { mutableStateOf(false) }

    LaunchedEffect(pendingSubmissionsSummary) {
        if (pendingSubmissionsSummary?.hasPendingSubmissions() == false) {
            isExiting = true
            delay(3.seconds)
            onExit()
        } else {
            isExiting = false
        }
    }

    BaseDialog(
        title = stringResource(R.string.retroachievements_unsynchronized_data),
        onDismiss = { },
        content = {
            Box(modifier = Modifier.padding(it).animateContentSize()) {
                if (isExiting) {
                    ExitingInformation()
                } else {
                    Content(
                        initialPendingSubmissionsSummary = initialPendingSubmissionsSummary,
                        pendingSubmissionsSummary = pendingSubmissionsSummary,
                    )
                }
            }
        },
        buttons = {
            DialogButton(
                text = stringResource(R.string.cancel),
                onClick = onCancel,
            )
            DialogButton(
                text = stringResource(R.string.exit),
                onClick = onExit,
            )
        },
    )
}

@Composable
private fun Content(
    initialPendingSubmissionsSummary: RetroAchievementsSubmissionHandler.PendingSubmissionsSummary?,
    pendingSubmissionsSummary: RetroAchievementsSubmissionHandler.PendingSubmissionsSummary?,
) {
    val baseLineHeight = MaterialTheme.typography.body1.lineHeight
    val resources = LocalResources.current
    val rotationAnimation = rememberInfiniteTransition("spinner-rotation")

    val textContent = remember(pendingSubmissionsSummary) {
        val pendingAchievements = pendingSubmissionsSummary?.pendingAchievements ?: 0
        val pendingLeaderboardSubmissions = pendingSubmissionsSummary?.pendingLeaderboardSubmissions ?: 0

        buildAnnotatedString {
            if ((initialPendingSubmissionsSummary?.pendingAchievements ?: 0) > 0) {
                withStyle(ParagraphStyle(lineHeight = baseLineHeight * 1.5f, textIndent = TextIndent(Bullet.DefaultIndentation))) {
                    appendInlineContent("pending-achievement")
                    append("\u2003")
                    append(resources.getQuantityString(R.plurals.retroachievements_pending_achievement_unlocks, pendingAchievements, pendingAchievements))
                    append(" ")
                    if (pendingAchievements > 0) {
                        appendInlineContent("spinner")
                    } else {
                        appendInlineContent("checkmark")
                    }
                }
            }
            if ((initialPendingSubmissionsSummary?.pendingLeaderboardSubmissions ?: 0) > 0) {
                withStyle(ParagraphStyle(lineHeight = baseLineHeight * 1.5f, textIndent = TextIndent(Bullet.DefaultIndentation))) {
                    appendInlineContent("pending-leaderboard")
                    append("\u2003")
                    append(resources.getQuantityString(R.plurals.retroachievements_pending_leaderboard_entries, pendingLeaderboardSubmissions, pendingLeaderboardSubmissions))
                    append(" ")
                    if (pendingLeaderboardSubmissions > 0) {
                        appendInlineContent("spinner")
                    } else {
                        appendInlineContent("checkmark")
                    }
                }
            }
        }
    }

    Column {
        Text(
            text = stringResource(R.string.retroachievements_unsynchronized_data_info),
            style = MaterialTheme.typography.body1,
        )
        Text(
            modifier = Modifier.padding(vertical = 8.dp),
            text = textContent,
            style = MaterialTheme.typography.body1,
            inlineContent = mapOf(
                "pending-achievement" to InlineTextContent(Placeholder(baseLineHeight, baseLineHeight, PlaceholderVerticalAlign.Center)) {
                    Icon(
                        modifier = Modifier.fillMaxSize(),
                        painter = painterResource(R.drawable.ic_trophy),
                        contentDescription = null,
                        tint = MaterialTheme.colors.secondary,
                    )
                },
                "pending-leaderboard" to InlineTextContent(Placeholder(baseLineHeight, baseLineHeight, PlaceholderVerticalAlign.Center)) {
                    Icon(
                        modifier = Modifier.fillMaxSize(),
                        imageVector = Icons.Default.Leaderboard,
                        contentDescription = null,
                        tint = MaterialTheme.colors.secondary,
                    )
                },
                "spinner" to InlineTextContent(Placeholder(baseLineHeight, baseLineHeight, PlaceholderVerticalAlign.Center)) {
                    val rotation by rotationAnimation.animateFloat(
                        initialValue = 0f,
                        targetValue = -360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart,
                        ),
                    )

                    Icon(
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            rotationZ = rotation
                        },
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = MaterialTheme.colors.secondary,
                    )
                },
                "checkmark" to InlineTextContent(Placeholder(MaterialTheme.typography.body1.fontSize, MaterialTheme.typography.body1.fontSize, PlaceholderVerticalAlign.Center)) {
                    Image(
                        modifier = Modifier.fillMaxSize(),
                        painter = painterResource(id = R.drawable.ic_completed),
                        contentDescription = null,
                    )
                }
            )
        )
        Text(
            text = buildAnnotatedString {
                appendLine(resources.getString(R.string.retroachievements_unsynchronized_data_connect_internet))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(resources.getString(R.string.retroachievements_unsynchronized_data_loss_info))
                }
            },
            style = MaterialTheme.typography.body1,
        )
    }

}

@Composable
private fun ExitingInformation() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = buildAnnotatedString {
                append(stringResource(R.string.retroachievements_unsynchronized_data_synchronized))
                append(" ")
                appendInlineContent("checkmark")
            },
            style = MaterialTheme.typography.body1,
            fontWeight = FontWeight.Bold,
            inlineContent = mapOf(
                "checkmark" to InlineTextContent(Placeholder(MaterialTheme.typography.body1.fontSize, MaterialTheme.typography.body1.fontSize, PlaceholderVerticalAlign.Center)) {
                    Image(
                        modifier = Modifier.fillMaxSize(),
                        painter = painterResource(id = R.drawable.ic_completed),
                        contentDescription = null,
                    )
                }
            )
        )
        Text(
            text = stringResource(R.string.retroachievements_unsynchronized_data_exiting),
            style = MaterialTheme.typography.body1,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PendingSubmissionsDialogPreview() {
    MelonTheme {
        Content(
            initialPendingSubmissionsSummary = RetroAchievementsSubmissionHandler.PendingSubmissionsSummary(
                pendingAchievements = 3,
                pendingLeaderboardSubmissions = 1,
            ),
            pendingSubmissionsSummary = RetroAchievementsSubmissionHandler.PendingSubmissionsSummary(
                pendingAchievements = 3,
                pendingLeaderboardSubmissions = 0,
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PendingSubmissionsDialogExitingPreview() {
    MelonTheme {
        ExitingInformation()
    }
}