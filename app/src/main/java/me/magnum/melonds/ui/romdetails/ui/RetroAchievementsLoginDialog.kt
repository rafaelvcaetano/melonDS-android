package me.magnum.melonds.ui.romdetails.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.autofill.contentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.magnum.melonds.R
import me.magnum.melonds.ui.common.melonOutlinedTextFieldColors
import me.magnum.melonds.ui.common.melonTextButtonColors

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RetroAchievementsLoginDialog(
    onDismiss: () -> Unit,
    onLogin: (username: String, password: String) -> Unit,
) {
    var username by rememberSaveable {
        mutableStateOf("")
    }
    var password by remember {
        mutableStateOf("")
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(Modifier.widthIn(max = 450.dp).fillMaxWidth(0.85f)) {
            Column {
                Box(
                    modifier = Modifier
                        .heightIn(min = 64.dp)
                        .padding(horizontal = 24.dp),
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .contentType(ContentType.Username),
                        value = username,
                        onValueChange = { username = it },
                        colors = melonOutlinedTextFieldColors(),
                        label = {
                            Text(text = stringResource(id = R.string.username))
                        }
                    )

                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .contentType(ContentType.Password),
                        value = password,
                        onValueChange = { password = it },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Password
                        ),
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
                    TextButton(
                        onClick = onDismiss,
                        colors = melonTextButtonColors(),
                    ) {
                        Text(
                            text = stringResource(id = R.string.cancel).uppercase(),
                            style = MaterialTheme.typography.button,
                        )
                    }

                    TextButton(
                        onClick = { onLogin(username, password) },
                        colors = melonTextButtonColors(),
                    ) {
                        Text(
                            text = stringResource(id = R.string.login).uppercase(),
                            style = MaterialTheme.typography.button,
                        )
                    }
                }
            }
        }
    }
}