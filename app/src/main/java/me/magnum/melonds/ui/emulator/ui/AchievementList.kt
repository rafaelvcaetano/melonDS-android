package me.magnum.melonds.ui.emulator.ui

import androidx.compose.animation.core.animate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.magnum.melonds.R
import me.magnum.melonds.ui.common.melonButtonColors
import me.magnum.melonds.ui.romdetails.model.AchievementBucketUiModel
import me.magnum.melonds.ui.romdetails.model.AchievementSetUiModel
import me.magnum.melonds.ui.romdetails.model.RomRetroAchievementsUiState
import me.magnum.melonds.ui.romdetails.ui.AchievementsMultiSetTabRow
import me.magnum.melonds.ui.romdetails.ui.RomAchievementUi
import me.magnum.rcheevosapi.model.RAAchievement
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val DISMISS_DISTANCE_THRESHOLD = 150.dp
private val FLING_DISMISS_VELOCITY_THRESHOLD = 150.dp
private val LIST_CONTENT_PADDING = 40.dp

private val CONTENT_TYPE_ACHIEVEMENT = "achievement"
private val CONTENT_TYPE_BUCKET_HEADER = "bucket-header"

@Composable
fun AchievementList(
    modifier: Modifier,
    state: RomRetroAchievementsUiState,
    onViewAchievement: (RAAchievement) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    var offsetY by remember { mutableFloatStateOf(0f) }
    val listAtTop by remember {
        derivedStateOf { lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0 }
    }

    val (dismissDistanceThresholdPx, flingDismissVelocityThresholdPx) = with(LocalDensity.current) {
        DISMISS_DISTANCE_THRESHOLD.toPx() to FLING_DISMISS_VELOCITY_THRESHOLD.toPx()
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Only handle downward scrolling when at the top of the list
                return if (listAtTop) {
                    if (available.y > 0) {
                        // Scroll down
                        val delta = available.y
                        offsetY += delta

                        Offset(0f, delta)
                    } else if (available.y < 0 && offsetY > 0) {
                        // Scroll up, but only if the list was being dismissed (offsetY > 0)
                        val delta = max(available.y, -offsetY)
                        offsetY += delta

                        Offset(0f, delta)
                    } else {
                        Offset.Zero
                    }
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // Handle drag release
                if (offsetY > 0f) {
                    coroutineScope.launch {
                        if (available.y > flingDismissVelocityThresholdPx) {
                            // High downwards flight velocity. Animate and dismiss
                            animate(initialValue = offsetY, targetValue = dismissDistanceThresholdPx, initialVelocity = available.y) { value, _ ->
                                offsetY = value
                            }
                            onDismiss()
                        } else if (offsetY > dismissDistanceThresholdPx) {
                            // User has scrolled being the dismiss threshold
                            onDismiss()
                        } else {
                            // Dismiss criteria not met. Animate back to original position
                            animate(initialValue = offsetY, targetValue = 0f) { value, _ ->
                                offsetY = value
                            }
                        }
                    }
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .offset { IntOffset(0, offsetY.roundToInt()) }
            .alpha( (1f - (offsetY / dismissDistanceThresholdPx)).coerceIn(0f, 1f) ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (state) {
            RomRetroAchievementsUiState.Loading -> {
                CircularProgressIndicator(
                    color = MaterialTheme.colors.secondary,
                )
            }
            is RomRetroAchievementsUiState.Ready -> {
                // This box is just a helper that is able to capture focus above the achievement list. Focus to this component is intercepted and the event is used to scroll
                // the list up if the list can still be scrolled. This allows users to fully scroll to the top using keyboard navigation even if there are non-focusable
                // elements at the top of the list
                Box(Modifier.focusable())
                Content(
                    modifier = Modifier.widthIn(max = 640.dp),
                    sets = state.sets,
                    onViewAchievement = onViewAchievement,
                    lazyListState = lazyListState,
                )
            }
            RomRetroAchievementsUiState.AchievementLoadError,
            RomRetroAchievementsUiState.LoggedOut,
            RomRetroAchievementsUiState.LoginError -> {
                LoadError(
                    modifier = Modifier.widthIn(max = 640.dp).padding(32.dp),
                    onRetry = onRetry,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Content(
    modifier: Modifier,
    sets: List<AchievementSetUiModel>,
    onViewAchievement: (RAAchievement) -> Unit,
    lazyListState: LazyListState,
) {
    var selectedSetId by rememberSaveable {
        mutableLongStateOf(sets.first().setId)
    }
    val selectedSet = remember(selectedSetId) {
        sets.first { it.setId == selectedSetId }
    }
    val backgroundColor = MaterialTheme.colors.background
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val coroutineScope = rememberCoroutineScope()
    val bringIntoViewSpec = remember(density) {
        val contentPadding = with(density) { LIST_CONTENT_PADDING.toPx() }
        AchievementListBringIntoViewSpec(contentPadding, contentPadding)
    }
    val scrollAmountByKeyboard = remember(density) {
        with(density) { 80.dp.toPx() }
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoViewSpec) {
        LazyColumn(
            modifier = modifier
                .focusProperties {
                    onExit = {
                        when (requestedFocusDirection) {
                            FocusDirection.Up -> {
                                if (lazyListState.canScrollBackward) {
                                    cancelFocusChange()
                                    coroutineScope.launch {
                                        lazyListState.animateScrollBy(-scrollAmountByKeyboard)
                                    }
                                } else if (lazyListState.firstVisibleItemIndex == 0) {
                                    // User is already at the top of the list. Prevent navigation out of the list
                                    cancelFocusChange()
                                }
                            }
                            FocusDirection.Down -> {
                                if (lazyListState.canScrollForward) {
                                    cancelFocusChange()
                                    coroutineScope.launch {
                                        lazyListState.animateScrollBy(scrollAmountByKeyboard)
                                    }
                                }
                            }
                        }
                    }
                }
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        val indexOffset = when (keyEvent.key) {
                            Key.ButtonL2 -> -1
                            Key.ButtonR2 -> 1
                            else -> 0
                        }.let {
                            // Flip offset direction for RTL layouts
                            if (layoutDirection == LayoutDirection.Ltr) it else -it
                        }

                        val selectedSetIndex = sets.indexOfFirst { it.setId == selectedSetId }
                        if (indexOffset != 0 && selectedSetIndex + indexOffset in sets.indices) {
                            selectedSetId = sets[selectedSetIndex + indexOffset].setId
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }
                .drawWithCache {
                    val fadeHeight = LIST_CONTENT_PADDING.value * this@drawWithCache.density
                    val topBrush = Brush.verticalGradient(
                        listOf(backgroundColor.copy(alpha = 0f), backgroundColor),
                        endY = fadeHeight,
                    )
                    val bottomBrush = Brush.verticalGradient(
                        listOf(backgroundColor, backgroundColor.copy(alpha = 0f)),
                        startY = size.height - fadeHeight,
                        endY = size.height
                    )

                    onDrawWithContent {
                        drawContent()
                        drawRect(
                            brush = topBrush,
                            blendMode = BlendMode.DstIn,
                            size = Size(size.width, fadeHeight),
                        )
                        drawRect(
                            brush = bottomBrush,
                            blendMode = BlendMode.DstIn,
                            topLeft = Offset(0f, size.height - fadeHeight),
                            size = Size(size.width, fadeHeight),
                        )
                    }
                },
            state = lazyListState,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = LIST_CONTENT_PADDING),
        ) {
            if (sets.size > 1) {
                item {
                    AchievementsMultiSetTabRow(
                        sets = sets,
                        selectedSetId = selectedSetId,
                        onSetSelected = { selectedSetId = it },
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }

            selectedSet.buckets.forEachIndexed { index, bucket ->
                item(contentType = CONTENT_TYPE_BUCKET_HEADER) {
                    Text(
                        modifier = Modifier.padding(start = 16.dp, top = if (index == 0) 0.dp else 16.dp, end = 16.dp, bottom = 4.dp).fillMaxWidth(),
                        text = getBucketTitle(bucket.bucket),
                        style = MaterialTheme.typography.h6,
                    )
                    Divider(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        color = MaterialTheme.colors.onSurface,
                    )
                }

                items(
                    items = bucket.achievements,
                    contentType = { CONTENT_TYPE_ACHIEVEMENT },
                ) {
                    RomAchievementUi(
                        modifier = Modifier.fillMaxWidth(),
                        achievementModel = it,
                        onViewAchievement = { onViewAchievement(it.actualAchievement()) },
                        badgeSize = 52.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun getBucketTitle(bucket: AchievementBucketUiModel.Bucket): String {
    return when (bucket) {
        AchievementBucketUiModel.Bucket.ActiveChallenges -> stringResource(R.string.retro_achievements_active_challenges)
        AchievementBucketUiModel.Bucket.RecentlyUnlocked -> stringResource(R.string.retro_achievements_recently_unlokced)
        AchievementBucketUiModel.Bucket.AlmostThere -> stringResource(R.string.retro_achievements_almost_there)
        AchievementBucketUiModel.Bucket.Locked -> stringResource(R.string.retro_achievements_locked)
        AchievementBucketUiModel.Bucket.Unlocked -> stringResource(R.string.retro_achievements_unlocked)
    }
}

@Composable
private fun LoadError(
    modifier: Modifier,
    onRetry: () -> Unit,
) {
    Column(
        modifier = modifier.background(MaterialTheme.colors.background),
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

/**
 * [BringIntoViewSpec] implementation that takes content padding into account. This means that focused items inside of a list are brought into view inside the useful list area
 * instead of leaving them on the edge, within the content padding area. The implementation was adapted from the default implementation of [BringIntoViewSpec].
 */
private class AchievementListBringIntoViewSpec(
    private val leadingPadding: Float,
    private val trailingPadding: Float,
) : BringIntoViewSpec {

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val trailingEdge = offset + size
        val leadingEdge = offset
        return when {

            // If the item is already visible, no need to scroll.
            leadingEdge >= leadingPadding && trailingEdge <= containerSize - trailingPadding -> 0f

            // If the item is visible but larger than the parent, we don't scroll.
            leadingEdge < leadingPadding && trailingEdge > containerSize - trailingPadding -> 0f

            // Find the minimum scroll needed to make one of the edges coincide with the
            // parent's
            // edge.
            abs(leadingEdge + leadingPadding) < abs(trailingEdge - (containerSize - trailingPadding)) -> leadingEdge - leadingPadding
            else -> trailingEdge - containerSize + trailingPadding
        }
    }
}