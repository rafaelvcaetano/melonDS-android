package me.magnum.melonds.ui.romdetails.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import me.magnum.melonds.R
import me.magnum.melonds.ui.common.autofill
import me.magnum.melonds.ui.common.melonOutlinedTextFieldColors
import me.magnum.melonds.ui.common.melonTextButtonColors

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RetroAchievementsLoginDialog(
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