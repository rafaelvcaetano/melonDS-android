package me.magnum.melonds.ui.cheats.ui.cheatform

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import me.magnum.melonds.R
import me.magnum.melonds.ui.cheats.model.CheatFormDialogState
import me.magnum.melonds.ui.cheats.model.CheatSubmissionForm
import me.magnum.melonds.ui.common.FullScreen
import me.magnum.melonds.ui.common.MelonPreviewSet
import me.magnum.melonds.ui.common.component.dialog.BaseDialog
import me.magnum.melonds.ui.common.component.dialog.DialogButton
import me.magnum.melonds.ui.common.component.text.OutlinedTextFieldWithError
import me.magnum.melonds.ui.common.melonOutlinedTextFieldColors
import me.magnum.melonds.ui.theme.MelonTheme

@Composable
fun CheatFormDialog(
    state: CheatFormDialogState,
    onDismiss: () -> Unit,
    onSaveCheat: (CheatSubmissionForm) -> Unit,
) {
    if (state != CheatFormDialogState.Hidden) {
        val cheatFormState = rememberCheatFormState(state)

        val isLargeScreen = LocalConfiguration.current.screenHeightDp >= 900 && LocalConfiguration.current.screenWidthDp >= 840
        if (isLargeScreen) {
            CheatFormPopupDialog(
                cheatFormState = cheatFormState,
                onDismiss = onDismiss,
                onSaveCheat = onSaveCheat,
            )
        } else {
            CheatFormFullscreenDialog(
                cheatFormState = cheatFormState,
                onDismiss = onDismiss,
                onSaveCheat = onSaveCheat,
            )
        }
    }
}

@Composable
private fun CheatFormPopupDialog(
    cheatFormState: CheatFormState,
    onDismiss: () -> Unit,
    onSaveCheat: (CheatSubmissionForm) -> Unit,
) {
    val validateAndSaveCheat = {
        if (cheatFormState.validateFields()) {
            val submissionForm = cheatFormState.buildSubmissionForm()
            onSaveCheat(submissionForm)
        }
    }

    BaseDialog(
        onDismiss = onDismiss,
        title = if (cheatFormState.isNewCheat) stringResource(R.string.new_cheat) else stringResource(R.string.edit_cheat),
        content = {
            CheatFormBody(
                modifier = Modifier.fillMaxWidth(),
                cheatFormState = cheatFormState,
                contentPadding = it,
                onDoneClick = validateAndSaveCheat,
            )
        },
        buttons = {
            DialogButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
            )
            DialogButton(
                text = stringResource(R.string.save),
                onClick = validateAndSaveCheat,
            )
        }
    )
}

@Composable
private fun CheatFormFullscreenDialog(
    cheatFormState: CheatFormState,
    onDismiss: () -> Unit,
    onSaveCheat: (CheatSubmissionForm) -> Unit,
) {
    val validateAndSaveCheat = {
        if (cheatFormState.validateFields()) {
            val submissionForm = cheatFormState.buildSubmissionForm()
            onSaveCheat(submissionForm)
        }
    }

    val systemUiController = rememberSystemUiController()
    val isSystemInDarkMode = isSystemInDarkTheme()

    DisposableEffect(Unit) {
        val originalState = systemUiController.statusBarDarkContentEnabled
        systemUiController.statusBarDarkContentEnabled = !isSystemInDarkMode

        onDispose {
            systemUiController.statusBarDarkContentEnabled = originalState
        }
    }

    FullScreen(onDismiss) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        val title = if (cheatFormState.isNewCheat) stringResource(R.string.new_cheat) else stringResource(R.string.edit_cheat)
                        Text(title)
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                painter = rememberVectorPainter(Icons.Default.Close),
                                contentDescription = stringResource(R.string.cancel),
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = validateAndSaveCheat,
                        ) {
                            Text(
                                text = stringResource(R.string.save).uppercase(),
                                color = MaterialTheme.colors.secondary,
                            )
                        }
                    },
                    backgroundColor = MaterialTheme.colors.surface,
                    elevation = 0.dp,
                    windowInsets = WindowInsets.safeDrawing.exclude(WindowInsets(bottom = Int.MAX_VALUE)),
                )
            },
            backgroundColor = MaterialTheme.colors.surface,
            contentWindowInsets = WindowInsets.safeDrawing,
        ) { padding ->
            CheatFormBody(
                modifier = Modifier.consumeWindowInsets(padding).verticalScroll(rememberScrollState()),
                cheatFormState = cheatFormState,
                contentPadding = PaddingValues(
                    start = padding.calculateStartPadding(LocalLayoutDirection.current) + 16.dp,
                    top = padding.calculateTopPadding() + 16.dp,
                    end = padding.calculateEndPadding(LocalLayoutDirection.current) + 16.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp
                ),
                onDoneClick = validateAndSaveCheat,
            )
        }
    }
}

@Composable
private fun CheatFormBody(
    modifier: Modifier,
    cheatFormState: CheatFormState,
    contentPadding: PaddingValues,
    onDoneClick: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = modifier.padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CompositionLocalProvider(
            LocalTextSelectionColors provides TextSelectionColors(
                handleColor = MaterialTheme.colors.secondary,
                backgroundColor = LocalTextSelectionColors.current.backgroundColor,
            )
        ) {
            OutlinedTextFieldWithError(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                value = cheatFormState.cheatName,
                onValueChange = {
                    cheatFormState.cheatName = it
                    if (cheatFormState.cheatNameFieldError != null) {
                        cheatFormState.validateCheatNameField()
                    }
                },
                isError = cheatFormState.cheatNameFieldError != null,
                label = { Text(stringResource(R.string.cheat_name)) },
                error = {
                    Text(stringResource(R.string.error_name_cannot_be_empty))
                },
                colors = melonOutlinedTextFieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                singleLine = true,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = cheatFormState.cheatDescription,
                onValueChange = { cheatFormState.cheatDescription = it },
                label = { Text(stringResource(R.string.description)) },
                colors = melonOutlinedTextFieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            )
            OutlinedTextFieldWithError(
                modifier = Modifier.fillMaxWidth(),
                value = cheatFormState.cheatCode,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                isError = cheatFormState.cheatCodeFieldError != null,
                onValueChange = {
                    cheatFormState.cheatCode = formatCheatCode(it)
                    if (cheatFormState.cheatCodeFieldError != null) {
                        cheatFormState.validateCheatCodeField()
                    }
                },
                label = { Text(stringResource(R.string.cheat_code)) },
                error = {
                    when (cheatFormState.cheatCodeFieldError) {
                        CheatFormState.Companion.FieldError.CANNOT_BE_EMPTY -> Text(stringResource(R.string.error_code_cannot_be_empty))
                        CheatFormState.Companion.FieldError.INVALID_FORMAT -> Text(stringResource(R.string.error_code_invalid_format))
                        else -> Unit
                    }
                },
                colors = melonOutlinedTextFieldColors(),
                minLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDoneClick() }),
            )
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@MelonPreviewSet
@Composable
private fun PreviewCheatForm() {
    MelonTheme {
        CheatFormBody(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            cheatFormState = CheatFormState(isNewCheat = true),
            onDoneClick = { },
        )
    }
}