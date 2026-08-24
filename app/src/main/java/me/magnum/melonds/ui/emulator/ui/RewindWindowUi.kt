package me.magnum.melonds.ui.emulator.ui

import android.content.Context
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.rewind.RewindWindowPosition
import me.magnum.melonds.ui.common.CenteredBringIntoViewSpec
import me.magnum.melonds.ui.emulator.model.RewindWindowState
import me.magnum.melonds.ui.emulator.rewind.model.RewindSaveState
import me.magnum.melonds.ui.emulator.rewind.model.RewindWindow
import java.text.DecimalFormat
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val SECONDS_FORMATTER = DecimalFormat("#0.##")

private val ITEM_WIDTH = 95.dp
private val ITEM_HORIZONTAL_PADDING = 8.dp
private val ITEM_INNER_PADDING = 8.dp
private val ITEM_TOTAL_WIDTH = ITEM_WIDTH + (ITEM_HORIZONTAL_PADDING + ITEM_INNER_PADDING) * 2

@Composable
fun RewindWindowUi(
    state: RewindWindowState,
    onRewindSaveStateSelected: (RewindSaveState) -> Unit,
    onDismiss: () -> Unit,
) {
    val visible = state is RewindWindowState.Visible

    // Remember the last visible rewind window so content stays during exit animation
    var lastRewindWindow by remember { mutableStateOf<RewindWindow?>(null) }
    var lastWindowPosition by remember { mutableStateOf(RewindWindowPosition.BOTTOM) }
    if (state is RewindWindowState.Visible) {
        lastRewindWindow = state.rewindWindow
        lastWindowPosition = state.windowPosition
    }

    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(visible) {
        if (visible) {
            animationProgress.animateTo(1f, tween(250))
        } else {
            animationProgress.animateTo(0f, tween(250))
        }
    }

    var listHeightPx by remember { mutableIntStateOf(0) }

    PredictiveBackHandler(enabled = visible) { progress ->
        try {
            progress.collect { backEvent ->
                animationProgress.snapTo(1f - backEvent.progress)
            }
            onDismiss()
        } catch (_: CancellationException) {
            animationProgress.animateTo(1f, tween(200))
        }
    }

    if (animationProgress.value > 0f || visible) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(animationProgress.value)
                    .background(Color(0xAA000000))
                    .focusProperties { canFocus = false }
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = onDismiss,
                    ),
            )

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = when (lastWindowPosition) {
                    RewindWindowPosition.TOP -> Alignment.TopCenter
                    RewindWindowPosition.BOTTOM -> Alignment.BottomCenter
                },
            ) {
                val offsetY = when (lastWindowPosition) {
                    RewindWindowPosition.TOP -> -((1f - animationProgress.value) * listHeightPx).roundToInt()
                    RewindWindowPosition.BOTTOM -> ((1f - animationProgress.value) * listHeightPx).roundToInt()
                }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, offsetY) }
                        .alpha(animationProgress.value)
                        .onSizeChanged { listHeightPx = it.height },
                ) {
                    lastRewindWindow?.let {
                        RewindStateList(
                            rewindWindow = it,
                            onRewindSaveStateSelected = onRewindSaveStateSelected,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RewindStateList(
    rewindWindow: RewindWindow,
    onRewindSaveStateSelected: (RewindSaveState) -> Unit,
) {
    val reversedStates = remember(rewindWindow) { rewindWindow.rewindStates.asReversed() }
    val lastIndex = reversedStates.lastIndex
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = lastIndex)
    val firstItemFocusRequester = remember { FocusRequester() }
    val bringIntoViewSpec = remember { CenteredBringIntoViewSpec() }
    var pendingFocusRequest by remember { mutableStateOf(true) }

    val inputMode = LocalInputModeManager.current.inputMode

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Horizontal content padding so that edge items can be centered
        val horizontalPadding = (maxWidth - ITEM_TOTAL_WIDTH) / 2

        CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoViewSpec) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 16.dp),
            ) {
                itemsIndexed(
                    items = reversedStates,
                    key = { _, state -> state.frame },
                ) { index, state ->
                    RewindSaveStateItem(
                        modifier = Modifier.then(
                            if (index == lastIndex) {
                                Modifier.focusRequester(firstItemFocusRequester).onGloballyPositioned {
                                    if (pendingFocusRequest && inputMode == InputMode.Keyboard) {
                                        firstItemFocusRequester.requestFocus()
                                        pendingFocusRequest = false
                                    }
                                }
                            } else {
                                Modifier
                            }
                        ),
                        state = state,
                        rewindWindow = rewindWindow,
                        onSelected = { onRewindSaveStateSelected(state) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RewindSaveStateItem(
    modifier: Modifier = Modifier,
    state: RewindSaveState,
    rewindWindow: RewindWindow,
    onSelected: () -> Unit,
) {
    val context = LocalContext.current
    val screenshot = remember(state.frame) { state.screenshot }
    val duration = remember(state.frame) { rewindWindow.getDeltaFromEmulationTimeToRewindState(state) }
    val timeText = remember(duration) { getDurationString(context, duration) }
    var isFocused by remember { mutableStateOf(false) }

    val shape = RoundedCornerShape(8.dp)
    val backgroundColor = if (isFocused) Color.White else Color.Transparent
    val textColor = if (isFocused) Color.Black else Color.White

    Column(
        modifier = modifier
            .padding(horizontal = ITEM_HORIZONTAL_PADDING)
            .clip(shape)
            .background(backgroundColor, shape)
            .onFocusChanged {
                isFocused = it.isFocused
            }
            .clickable(onClick = onSelected)
            .padding(ITEM_INNER_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            modifier = Modifier.width(ITEM_WIDTH),
            bitmap = screenshot.asImageBitmap(),
            contentDescription = timeText,
            contentScale = ContentScale.FillWidth,
        )
        Text(
            text = timeText,
            color = textColor,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

private fun getDurationString(context: Context, duration: Duration): String {
    val minutes = duration.inWholeMinutes.toInt()
    return if (minutes >= 1) {
        val seconds = (duration.inWholeMilliseconds - minutes.minutes.inWholeMilliseconds) / 1000f
        context.getString(R.string.rewind_time_minutes_seconds, minutes, SECONDS_FORMATTER.format(seconds))
    } else {
        val seconds = duration.inWholeMilliseconds / 1000f
        context.getString(R.string.rewind_time_seconds, SECONDS_FORMATTER.format(seconds))
    }
}