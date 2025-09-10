package me.magnum.melonds.ui.inputsetup.ui

import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component1
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component2
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.InputConfig
import me.magnum.melonds.ui.common.MelonPreviewSet
import me.magnum.melonds.ui.inputsetup.InputSetupViewModel
import me.magnum.melonds.ui.theme.MelonTheme

@Composable
fun InputSetupScreen(
    viewModel: InputSetupViewModel,
    onBackClick: () -> Unit,
) {
    val inputConfig by viewModel.inputConfiguration.collectAsStateWithLifecycle()
    val inputUnderConfiguration by viewModel.inputUnderAssignment.collectAsStateWithLifecycle()
    val onInputAssignedEvent = viewModel.onInputAssignedEvent

    InputSetupScreenContent(
        inputConfig = inputConfig,
        inputUnderConfiguration = inputUnderConfiguration,
        onInputAssignedEvent = onInputAssignedEvent,
        onInputClick = viewModel::startInputAssignment,
        onClearInputClick = viewModel::clearInputAssignment,
        onCancelInputConfiguration = viewModel::stopInputAssignment,
        onBackClick = onBackClick,
    )
}

@Composable
private fun InputSetupScreenContent(
    inputConfig: List<InputConfig>,
    inputUnderConfiguration: Input?,
    onInputAssignedEvent: Flow<Input>,
    onInputClick: (Input) -> Unit,
    onClearInputClick: (Input) -> Unit,
    onCancelInputConfiguration: () -> Unit,
    onBackClick: () -> Unit,
) {
    val systemUiController = rememberSystemUiController()
    val focusManager = LocalFocusManager.current

    systemUiController.setStatusBarColor(MaterialTheme.colors.primaryVariant)
    systemUiController.isNavigationBarContrastEnforced = false

    BackHandler(enabled = inputUnderConfiguration != null) {
        // Prevent back navigation when user is configuring an input
    }
    LaunchedEffect(Unit) {
        onInputAssignedEvent.collect {
            focusManager.moveFocus(focusDirection = FocusDirection.Down)
        }
    }

    Scaffold(
        topBar = {
            Box(Modifier.background(MaterialTheme.colors.primaryVariant).statusBarsPadding()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.key_mapping)) },
                    backgroundColor = MaterialTheme.colors.primary,
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                painter = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack),
                                contentDescription = stringResource(R.string.clear),
                            )
                        }
                    },
                    windowInsets = WindowInsets.safeDrawing.exclude(WindowInsets(bottom = Int.MAX_VALUE)),
                )
            }
        },
        backgroundColor = MaterialTheme.colors.surface,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        Box(Modifier.fillMaxSize().consumeWindowInsets(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
            ) {
                items(
                    items = inputConfig,
                    key = { it.input },
                ) {
                    Input(
                        config = it,
                        isBeingConfigured = it.input == inputUnderConfiguration,
                        onClick = { onInputClick(it.input) },
                        onClearClick = { onClearInputClick(it.input) },
                    )
                }
            }

            if (inputUnderConfiguration != null) {
                WaitingForInputOverlay(onCancelInputConfiguration)
            }
        }
    }
}

@Composable
private fun Input(
    config: InputConfig,
    isBeingConfigured: Boolean,
    onClick: () -> Unit,
    onClearClick: () -> Unit,
) {
    val (main, clear) = remember { FocusRequester.createRefs() }

    Row(
        modifier = Modifier.focusRequester(main)
            .focusProperties { end = if (config.hasKeyAssigned()) clear else FocusRequester.Default }
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            val inputString = if (isBeingConfigured) {
                stringResource(R.string.press_any_button)
            } else {
                val assignments = listOf(config.assignment, config.altAssignment).filter { it != InputConfig.Assignment.None }
                if (assignments.isEmpty()) {
                    stringResource(R.string.not_set)
                } else {
                    assignments.joinToString(" / ") { assignment ->
                        when (assignment) {
                            is InputConfig.Assignment.Key -> {
                                val keyCodeString = KeyEvent.keyCodeToString(assignment.keyCode)
                                keyCodeString.replace("KEYCODE", "").replace("_", " ").trim()
                            }
                            is InputConfig.Assignment.Axis -> {
                                val axisString = MotionEvent.axisToString(assignment.axisCode)
                                val axisPrettyName = axisString.replace("_", " ").trim()
                                val prefix = if (assignment.direction == InputConfig.Assignment.Axis.Direction.NEGATIVE) "-" else ""
                                "$prefix$axisPrettyName"
                            }
                            InputConfig.Assignment.None -> ""
                        }
                    }
                }
            }

            Text(
                text = getInputName(config.input) ?: "",
                style = MaterialTheme.typography.body1,
            )

            Text(
                text = inputString,
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.colors.onBackground,
            )
        }
        if (config.hasKeyAssigned()) {
            IconButton(
                modifier = Modifier.focusRequester(clear).focusProperties { start = main },
                onClick = onClearClick,
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = stringResource(R.string.clear),
                )
            }
        }
    }
}

@Composable
private fun WaitingForInputOverlay(onCancel: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background.copy(alpha = 0.8f))
            .clickable(enabled = true, onClick = { })
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.waiting_for_input),
                style = MaterialTheme.typography.h6,
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onCancel) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    }
}

@Composable
private fun getInputName(input: Input): String? {
    val resource = when (input) {
        Input.A -> R.string.input_a
        Input.B -> R.string.input_b
        Input.X -> R.string.input_x
        Input.Y -> R.string.input_y
        Input.LEFT -> R.string.input_left
        Input.RIGHT -> R.string.input_right
        Input.UP -> R.string.input_up
        Input.DOWN -> R.string.input_down
        Input.L -> R.string.input_l
        Input.R -> R.string.input_r
        Input.START -> R.string.input_start
        Input.SELECT -> R.string.input_select
        Input.HINGE -> R.string.input_lid
        Input.PAUSE -> R.string.input_pause
        Input.FAST_FORWARD -> R.string.input_fast_forward
        Input.MICROPHONE -> R.string.input_microphone
        Input.RESET -> R.string.input_reset
        Input.SWAP_SCREENS -> R.string.input_swap_screens
        Input.QUICK_SAVE -> R.string.input_quick_save
        Input.QUICK_LOAD -> R.string.input_quick_load
        Input.REWIND -> R.string.rewind
        Input.REFRESH_EXTERNAL_SCREEN -> R.string.input_refresh_external_screen
        else -> return null
    }

    return stringResource(resource)
}

@MelonPreviewSet
@Composable
private fun PreviewInputSetupScreen() {
    MelonTheme {
        InputSetupScreenContent(
            inputConfig = listOf(
                InputConfig(
                    input = Input.A,
                    assignment = InputConfig.Assignment.Key(null, KeyEvent.KEYCODE_A),
                ),
                InputConfig(
                    input = Input.B,
                    assignment = InputConfig.Assignment.Key(null, KeyEvent.KEYCODE_B),
                ),
                InputConfig(
                    input = Input.X,
                    assignment = InputConfig.Assignment.Key(null, KeyEvent.KEYCODE_X),
                ),
                InputConfig(
                    input = Input.Y,
                    assignment = InputConfig.Assignment.Key(null, KeyEvent.KEYCODE_Y),
                ),
                InputConfig(
                    input = Input.UP,
                    assignment = InputConfig.Assignment.Key(null, KeyEvent.KEYCODE_DPAD_UP),
                    altAssignment = InputConfig.Assignment.Axis(null, MotionEvent.AXIS_Y, InputConfig.Assignment.Axis.Direction.NEGATIVE),
                ),
                InputConfig(
                    input = Input.DOWN,
                    assignment = InputConfig.Assignment.Key(null, KeyEvent.KEYCODE_DPAD_DOWN),
                    altAssignment = InputConfig.Assignment.Axis(null, MotionEvent.AXIS_Y, InputConfig.Assignment.Axis.Direction.POSITIVE),
                ),
                InputConfig(
                    input = Input.LEFT,
                    assignment = InputConfig.Assignment.Key(null, KeyEvent.KEYCODE_DPAD_LEFT),
                    altAssignment = InputConfig.Assignment.Axis(null, MotionEvent.AXIS_X, InputConfig.Assignment.Axis.Direction.NEGATIVE),
                ),
                InputConfig(
                    input = Input.RIGHT,
                    assignment = InputConfig.Assignment.Key(null, KeyEvent.KEYCODE_DPAD_RIGHT),
                    altAssignment = InputConfig.Assignment.Axis(null, MotionEvent.AXIS_X, InputConfig.Assignment.Axis.Direction.POSITIVE),
                ),
            ),
            inputUnderConfiguration = Input.B,
            onInputAssignedEvent = emptyFlow(),
            onInputClick = { },
            onClearInputClick = { },
            onCancelInputConfiguration = { },
            onBackClick = { },
        )
    }
}