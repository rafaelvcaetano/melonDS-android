package me.magnum.melonds.ui.emulator.ui

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.retroachievements.RAUserAchievement
import me.magnum.melonds.ui.common.melonButtonColors
import me.magnum.melonds.ui.romdetails.model.RomRetroAchievementsUiState
import me.magnum.melonds.ui.romdetails.ui.RomAchievementUi
import me.magnum.rcheevosapi.model.RAAchievement
import kotlin.math.max
import kotlin.math.roundToInt

private val DISMISS_DISTANCE_THRESHOLD = 150.dp
private val FLING_DISMISS_VELOCITY_THRESHOLD = 150.dp

@Composable
fun AchievementList(
    modifier: Modifier,
    state: RomRetroAchievementsUiState,
    activeChallenges: List<RAAchievement>,
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

    Box(
        modifier = modifier.fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .offset { IntOffset(0, offsetY.roundToInt()) }
            .alpha( (1f - (offsetY / dismissDistanceThresholdPx)).coerceIn(0f, 1f) )
    ) {
        when (state) {
            RomRetroAchievementsUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colors.secondary,
                )
            }
            is RomRetroAchievementsUiState.Ready -> {
                Content(
                    modifier = Modifier.widthIn(max = 640.dp)
                        .align(Alignment.Center),
                    achievements = state.achievements,
                    activeChallenges = activeChallenges,
                    onViewAchievement = onViewAchievement,
                    lazyListState = lazyListState,
                )
            }
            RomRetroAchievementsUiState.AchievementLoadError,
            RomRetroAchievementsUiState.LoggedOut,
            RomRetroAchievementsUiState.LoginError -> {
                LoadError(
                    modifier = Modifier.widthIn(max = 640.dp)
                        .padding(32.dp)
                        .align(Alignment.Center),
                    onRetry = onRetry,
                )
            }
        }
    }
}

@Composable
private fun Content(
    modifier: Modifier,
    achievements: List<RAUserAchievement>,
    activeChallenges: List<RAAchievement>,
    onViewAchievement: (RAAchievement) -> Unit,
    lazyListState: LazyListState,
) {
    val backgroundColor = MaterialTheme.colors.background
    val fillScreen = achievements.size <= 3
    LazyColumn(
        modifier = modifier
            .drawWithCache {
                val fadeHeight = 56 * density
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
        contentPadding = PaddingValues(vertical = 40.dp),
    ) {
        if (activeChallenges.isNotEmpty()) {
            item {
                Text(
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    text = stringResource(R.string.retro_achievements_active_challenges),
                    style = MaterialTheme.typography.h5,
                )
            }

            items(activeChallenges) {
                RomAchievementUi(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = if (fillScreen) 140.dp else 0.dp),
                    achievement = it,
                    showLocked = false,
                    onViewAchievement = { onViewAchievement(it) },
                    badgeSize = if (fillScreen) 96.dp else 52.dp,
                )
            }

            item {
                Divider(
                    modifier = Modifier.padding(vertical = 24.dp, horizontal = 8.dp),
                    color = MaterialTheme.colors.onSurface,
                )
            }
        }

        items(achievements) {
            RomAchievementUi(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (fillScreen) 140.dp else 0.dp),
                achievement = it.achievement,
                showLocked = !it.isUnlocked,
                onViewAchievement = { onViewAchievement(it.achievement) },
                badgeSize = if (fillScreen) 96.dp else 52.dp,
            )
        }
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
