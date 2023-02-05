package me.magnum.melonds.ui.romdetails.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.retroachievements.RAUserAchievement
import me.magnum.melonds.ui.common.MelonPreviewSet
import me.magnum.melonds.ui.common.melonButtonColors
import me.magnum.melonds.ui.romdetails.model.RomRetroAchievementsUiState
import me.magnum.melonds.ui.theme.MelonTheme

@Composable
fun RomRetroAchievementsUi(
    modifier: Modifier,
    retroAchievementsUiState: RomRetroAchievementsUiState,
    onLogin: (username: String, password: String) -> Unit,
) {
    when (retroAchievementsUiState) {
        is RomRetroAchievementsUiState.LoggedOut -> LoggedOut(
            modifier = modifier,
            onLogin = onLogin,
        )
        is RomRetroAchievementsUiState.Loading -> Loading(modifier)
        is RomRetroAchievementsUiState.Ready -> Ready(
            modifier = modifier,
            achievements = retroAchievementsUiState.achievements,
        )
        is RomRetroAchievementsUiState.LoginError -> LoginError(modifier)
        is RomRetroAchievementsUiState.AchievementLoadError -> LoadError(modifier)
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
                Text(text = stringResource(id = R.string.login))
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
                        text = stringResource(id = R.string.retro_achievements),
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
                        value = username,
                        onValueChange = { username = it },
                        label = {
                            Text(text = stringResource(id = R.string.username))
                        }
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        visualTransformation = PasswordVisualTransformation(),
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
        CircularProgressIndicator(color = MaterialTheme.colors.primary)
    }
}

@Composable
private fun Ready(
    modifier: Modifier,
    achievements: List<RAUserAchievement>,
) {
    LazyColumn(
        modifier = modifier,
    ) {
        items(
            items = achievements,
            key = { it.achievement.id },
        ) {

        }
    }
}

@Composable
private fun LoginError(
    modifier: Modifier
) {
    Text(text = "Login error")
}

@Composable
private fun LoadError(
    modifier: Modifier
) {
    Text(text = "Load error")
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