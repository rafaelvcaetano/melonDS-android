package me.magnum.melonds.ui.romdetails.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import me.magnum.melonds.R
import me.magnum.melonds.ui.common.MelonPreviewSet
import me.magnum.melonds.ui.common.autofill
import me.magnum.melonds.ui.common.melonButtonColors
import me.magnum.melonds.ui.common.melonOutlinedTextFieldColors
import me.magnum.melonds.ui.romdetails.model.RomAchievementsSummary
import me.magnum.melonds.ui.romdetails.model.RomRetroAchievementsUiState
import me.magnum.melonds.ui.theme.MelonTheme

private const val HEADER_ITEM_TYPE = "header"
private const val ACHIEVEMENT_ITEM_TYPE = "achievement"

@Composable
fun RomRetroAchievementsUi(
    modifier: Modifier,
    retroAchievementsUiState: RomRetroAchievementsUiState,
    onLogin: (username: String, password: String) -> Unit,
    onRetryLoad: () -> Unit,
) {
    when (retroAchievementsUiState) {
        is RomRetroAchievementsUiState.LoggedOut -> LoggedOut(
            modifier = modifier,
            onLogin = onLogin,
        )
        is RomRetroAchievementsUiState.Loading -> Loading(modifier)
        is RomRetroAchievementsUiState.Ready -> {
            if (retroAchievementsUiState.achievements.isEmpty()) {
                NoAchievements(modifier)
            } else {
                Ready(
                    modifier = modifier,
                    content = retroAchievementsUiState,
                )
            }
        }
        is RomRetroAchievementsUiState.LoginError -> LoginError(modifier = modifier, onLogin = onLogin)
        is RomRetroAchievementsUiState.AchievementLoadError -> LoadError(modifier = modifier, onRetry = onRetryLoad)
    }
}

@Composable
private fun LoggedOut(
    modifier: Modifier,
    onLogin: (username: String, password: String) -> Unit,
) {
    var showLoginPopup by remember {
        mutableStateOf(false)
    }

    Box(
        modifier = modifier.padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(id = R.string.retro_achievements_login_description),
                textAlign = TextAlign.Center,
            )

            Button(
                onClick = { showLoginPopup = true },
                colors = melonButtonColors(),
            ) {
                Text(text = stringResource(id = R.string.login_with_retro_achievements).uppercase())
            }
        }
    }

    if (showLoginPopup) {
        LoginPopup(
            onDismiss = { showLoginPopup = false },
            onLogin = { username, password ->
                onLogin(username, password)
                showLoginPopup = false
            },
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LoginPopup(
    onDismiss: () -> Unit,
    onLogin: (username: String, password: String) -> Unit,
) {
    var username by remember {
        mutableStateOf("")
    }
    var password by remember {
        mutableStateOf("")
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 64.dp)
                        .padding(start = 24.dp, end = 24.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        modifier = Modifier,
                        text = stringResource(id = R.string.login_with_retro_achievements),
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OutlinedTextField(
                        modifier = Modifier.autofill(
                            autofillTypes = listOf(AutofillType.Username),
                            onFill = { username = it },
                        ),
                        value = username,
                        onValueChange = { username = it },
                        colors = melonOutlinedTextFieldColors(),
                        label = {
                            Text(text = stringResource(id = R.string.username))
                        }
                    )

                    OutlinedTextField(
                        modifier = Modifier.autofill(
                            autofillTypes = listOf(AutofillType.Password),
                            onFill = { password = it },
                        ),
                        value = password,
                        onValueChange = { password = it },
                        visualTransformation = PasswordVisualTransformation(),
                        colors = melonOutlinedTextFieldColors(),
                        label = {
                            Text(text = stringResource(id = R.string.password))
                        }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(id = R.string.cancel).uppercase(),
                            style = MaterialTheme.typography.button,
                            color = MaterialTheme.colors.secondary,
                        )
                    }

                    TextButton(onClick = { onLogin(username, password) }) {
                        Text(
                            text = stringResource(id = R.string.login).uppercase(),
                            style = MaterialTheme.typography.button,
                            color = MaterialTheme.colors.secondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Loading(modifier: Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colors.secondary)
    }
}

@Composable
private fun NoAchievements(modifier: Modifier) {
    Box(
        modifier = modifier.padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(id = R.string.retro_achievements_no_achievements),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Ready(
    modifier: Modifier,
    content: RomRetroAchievementsUiState.Ready,
) {
    LazyColumn(
        modifier = modifier,
    ) {
        item(contentType = HEADER_ITEM_TYPE) {
            Header(
                modifier = Modifier.fillMaxWidth(),
                achievementsSummary = content.summary,
            )
            Divider(Modifier.fillMaxWidth())
        }

        items(
            items = content.achievements,
            key = { it.achievement.id },
            contentType = { ACHIEVEMENT_ITEM_TYPE },
        ) {
            RomAchievementUi(modifier = Modifier.fillMaxWidth(), userAchievement = it)
        }
    }
}

@Composable
private fun Header(
    modifier: Modifier,
    achievementsSummary: RomAchievementsSummary,
) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = buildAnnotatedString {
                appendInlineContent("icon-points")
                append(' ')
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(achievementsSummary.totalPoints.toString())
                }
                append(' ')
                append(stringResource(id = R.string.points_abbreviated))
            },
            inlineContent = mapOf(
                "icon-points" to InlineTextContent(Placeholder(MaterialTheme.typography.body1.fontSize, MaterialTheme.typography.body1.fontSize, PlaceholderVerticalAlign.Center)) {
                    Image(
                        modifier = Modifier.fillMaxSize(),
                        painter = painterResource(id = R.drawable.ic_points),
                        contentDescription = null,
                    )
                }
            )
        )

        Text(
            text = buildAnnotatedString {
                appendInlineContent(id = "icon-completed", alternateText = stringResource(id = R.string.completed))
                append(' ')
                append(stringResource(id = R.string.completed_achievements, achievementsSummary.completedAchievements, achievementsSummary.totalAchievements, achievementsSummary.completedPercentage))
            },
            inlineContent = mapOf(
                "icon-completed" to InlineTextContent(Placeholder(MaterialTheme.typography.body1.fontSize, MaterialTheme.typography.body1.fontSize, PlaceholderVerticalAlign.Center)) {
                    Image(
                        modifier = Modifier.fillMaxSize(),
                        painter = painterResource(id = R.drawable.ic_completed),
                        contentDescription = null,
                    )
                }
            )
        )

        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50)),
            progress = achievementsSummary.completedAchievements / achievementsSummary.totalAchievements.toFloat(),
            color = MaterialTheme.colors.secondary,
        )
    }
}

@Composable
private fun LoginError(
    modifier: Modifier,
    onLogin: (username: String, password: String) -> Unit,
) {
    var showLoginPopup by remember {
        mutableStateOf(false)
    }

    Box(
        modifier = modifier.padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(id = R.string.retro_achievements_login_error),
                textAlign = TextAlign.Center,
            )

            Button(
                onClick = { showLoginPopup = true },
                colors = melonButtonColors(),
            ) {
                Text(text = stringResource(id = R.string.login_with_retro_achievements).uppercase())
            }
        }
    }

    if (showLoginPopup) {
        LoginPopup(
            onDismiss = { showLoginPopup = false },
            onLogin = { username, password ->
                onLogin(username, password)
                showLoginPopup = false
            },
        )
    }
}

@Composable
private fun LoadError(
    modifier: Modifier,
    onRetry: () -> Unit,
) {
    Box(
        modifier = modifier.padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(id = R.string.retro_achievements_load_error),
                textAlign = TextAlign.Center,
            )

            Button(
                onClick = onRetry,
                colors = melonButtonColors(),
            ) {
                Text(text = stringResource(id = R.string.retry).uppercase())
            }
        }
    }
}

@MelonPreviewSet
@Composable
private fun PreviewLoggedOut() {
    MelonTheme {
        LoggedOut(
            modifier = Modifier.fillMaxSize(),
            onLogin = { _, _ -> },
        )
    }
}

@MelonPreviewSet
@Composable
private fun PreviewLoginError() {
    MelonTheme {
        LoginError(
            modifier = Modifier.fillMaxSize(),
            onLogin = { _, _ -> },
        )
    }
}

@MelonPreviewSet
@Composable
private fun PreviewLoadError() {
    MelonTheme {
        LoadError(
            modifier = Modifier.fillMaxSize(),
            onRetry = { },
        )
    }
}