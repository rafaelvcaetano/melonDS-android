package me.magnum.melonds.ui.common.component.dialog

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import me.magnum.melonds.R
import me.magnum.melonds.ui.common.melonOutlinedTextFieldColors

@Composable
fun TextInputDialog(
    title: String,
    dialogState: TextInputDialogState,
    allowEmpty: Boolean = false,
    onDelete: (() -> Unit)? = null,
) {
    if (dialogState.isDialogVisible) {
        BaseDialog(
            title = title,
            onDismiss = dialogState::cancel,
            content = { padding ->
                CompositionLocalProvider(
                    LocalTextSelectionColors provides TextSelectionColors(
                        handleColor = MaterialTheme.colors.secondary,
                        backgroundColor = LocalTextSelectionColors.current.backgroundColor,
                    )
                ) {
                    val focusRequester = remember { FocusRequester() }
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth()
                            .padding(padding)
                            .focusRequester(focusRequester),
                        value = dialogState.textField,
                        onValueChange = { dialogState.textField = it },
                        colors = melonOutlinedTextFieldColors(),
                    )

                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                    }
                }
            },
            buttons = {
                DialogButton(
                    text = stringResource(R.string.cancel),
                    onClick = { dialogState.cancel() },
                )
                if (onDelete != null) {
                    DialogButton(
                        text = stringResource(R.string.delete),
                        onClick = {
                            onDelete()
                            dialogState.cancel()
                        },
                    )
                }
                DialogButton(
                    text = stringResource(R.string.ok),
                    enabled = allowEmpty || dialogState.textField.text.isNotEmpty(),
                    onClick = { dialogState.confirm() },
                )
            }
        )
    }
}

@Composable
fun rememberTextInputDialogState(): TextInputDialogState {
    return remember {
        TextInputDialogState()
    }
}

class TextInputDialogState {

    internal var isDialogVisible by mutableStateOf(false)
    internal var textField by mutableStateOf(TextFieldValue())
    private var onConfirmCallback by mutableStateOf<((String) -> Unit)?>(null)

    fun show(initialText: String, onConfirm: (String) -> Unit) {
        if (isDialogVisible) return

        textField = TextFieldValue(text = initialText, selection = TextRange(initialText.length))
        onConfirmCallback = onConfirm
        isDialogVisible = true
    }

    internal fun cancel() {
        isDialogVisible = false
    }

    internal fun confirm() {
        onConfirmCallback?.invoke(textField.text)
        isDialogVisible = false
        onConfirmCallback = null
    }
}