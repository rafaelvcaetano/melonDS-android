package me.magnum.melonds.ui.common.component.dialog

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import me.magnum.melonds.R
import me.magnum.melonds.ui.common.melonOutlinedTextFieldColors

@Composable
fun TextInputDialog(
    title: String,
    dialogState: TextInputDialogState,
    textValidator: (String) -> Boolean = { it.isNotEmpty() },
    onDelete: (() -> Unit)? = null,
) {
    if (dialogState.isDialogVisible) {
        var hasError by remember { mutableStateOf(false) }

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
                        onValueChange = {
                            dialogState.textField = it
                            hasError = !textValidator(it.text)
                        },
                        isError = hasError,
                        colors = melonOutlinedTextFieldColors(),
                        keyboardOptions = dialogState.keyboardOptions,
                        keyboardActions = KeyboardActions(onDone = { if (!hasError) dialogState.confirm() }),
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
                    enabled = !hasError,
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
    internal var keyboardOptions by mutableStateOf(KeyboardOptions(imeAction = ImeAction.Done))
    private var onConfirmCallback by mutableStateOf<((String) -> Unit)?>(null)
    private var onCancelCallback by mutableStateOf<(() -> Unit)?>(null)

    fun show(
        initialText: String,
        onConfirm: (String) -> Unit,
        onCancel: () -> Unit = { },
        keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    ) {
        if (isDialogVisible) return

        textField = TextFieldValue(text = initialText, selection = TextRange(initialText.length))
        onConfirmCallback = onConfirm
        onCancelCallback = onCancel
        this.keyboardOptions = keyboardOptions
        isDialogVisible = true
    }

    internal fun cancel() {
        isDialogVisible = false
        onCancelCallback?.invoke()
        onConfirmCallback = null
        onCancelCallback = null
    }

    internal fun confirm() {
        onConfirmCallback?.invoke(textField.text)
        isDialogVisible = false
        onConfirmCallback = null
        onCancelCallback = null
    }
}