package me.magnum.melonds.ui.romdetails.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch
import me.magnum.melonds.R
import me.magnum.melonds.ui.romdetails.model.AchievementSetUiModel
import kotlin.math.ceil
import kotlin.math.floor

@Composable
fun AchievementsMultiSetTabRow(
    sets: List<AchievementSetUiModel>,
    selectedSetId: Long,
    onSetSelected: (Long) -> Unit,
) {
    val backgroundColor = MaterialTheme.colors.onBackground
    val selectorIndicatorColor = MaterialTheme.colors.secondary
    val selectedSetIndex = remember(sets, selectedSetId) {
        sets.indexOfFirst { it.setId == selectedSetId }
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val transition = updateTransition(isFocused)
    val animatedSelectedSetIndex by animateFloatAsState(selectedSetIndex.toFloat(), label = "Tab position")
    val selectorIndicatorAlpha by transition.animateFloat { if (it) 1f else 0.75f }
    val selectorWidthAnimation = remember { Animatable(-1f) }
    val listState = rememberLazyListState()
    val layoutDirection = LocalLayoutDirection.current

    LaunchedEffect(selectedSetIndex) {
        launch {
            listState.animateScrollToItem(selectedSetIndex)
        }

        var selectedTabItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == selectedSetIndex }
        while (selectedTabItem == null) {
            awaitFrame()
            selectedTabItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == selectedSetIndex }
        }

        if (selectorWidthAnimation.value == -1f) {
            selectorWidthAnimation.snapTo(selectedTabItem.size.toFloat())
        } else {
            selectorWidthAnimation.animateTo(selectedTabItem.size.toFloat())
        }
    }

    LazyRow(
        modifier = Modifier
            .focusable(interactionSource = interactionSource)
            .selectableGroup()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && isFocused) {
                    val indexOffset = when (keyEvent.key) {
                        Key.DirectionLeft -> -1
                        Key.DirectionRight -> 1
                        else -> 0
                    }.let {
                        // Flip offset direction for RTL layouts
                        if (layoutDirection == LayoutDirection.Ltr) it else -it
                    }

                    if (indexOffset != 0 && selectedSetIndex + indexOffset in sets.indices) {
                        onSetSelected(sets[selectedSetIndex + indexOffset].setId)
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
            .padding(vertical = 16.dp)
            .drawWithContent {
                val backgroundStartOffset = if (listState.firstVisibleItemIndex == 0) {
                    -listState.firstVisibleItemScrollOffset.toFloat() + listState.layoutInfo.beforeContentPadding
                } else {
                    // Move background edge out of view just enough to hide rounded corners
                    -size.height / 2f
                }

                val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.maxByOrNull { it.offset }
                val backgroundEndOffset = if (lastVisibleItem?.index == sets.lastIndex) {
                    lastVisibleItem.offset.toFloat() + lastVisibleItem.size + listState.layoutInfo.afterContentPadding
                } else {
                    // Move background edge out of view just enough to hide rounded corners
                    size.width + size.height / 2f
                }

                val backgroundWidth = backgroundEndOffset - backgroundStartOffset
                val backgroundLeftOffset = if (layoutDirection == LayoutDirection.Ltr) {
                    backgroundStartOffset
                } else {
                    size.width - backgroundEndOffset
                }

                // Draw background
                drawRoundRect(
                    color = backgroundColor,
                    alpha = 0.15f,
                    topLeft = Offset(backgroundLeftOffset, 0f),
                    size = size.copy(width = backgroundWidth),
                    cornerRadius = CornerRadius(size.height / 2f),
                )

                // Draw selector indicator
                val selectorInBounds = animatedSelectedSetIndex <= (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.toFloat() ?: Float.MAX_VALUE) &&
                        animatedSelectedSetIndex + 0.999f > (listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index?.toFloat() ?: Float.MIN_VALUE)
                if (selectorInBounds) {
                    val selectorIndicatorOffset = if (animatedSelectedSetIndex < listState.layoutInfo.visibleItemsInfo.first().index) {
                        // Handle scenario where the indicator is partially out of bounds on the left side
                        val lastVisibleItemInIndicatorBounds = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == animatedSelectedSetIndex.toInt() + 1 }
                        if (lastVisibleItemInIndicatorBounds == null) {
                            // This is the edge case where the indicator is navigating to the last tab
                            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.last()
                            lastVisibleItem.offset - (ceil(animatedSelectedSetIndex) - animatedSelectedSetIndex) * lastVisibleItem.size
                        } else {
                            lastVisibleItemInIndicatorBounds.offset - (ceil(animatedSelectedSetIndex) - animatedSelectedSetIndex) * lastVisibleItemInIndicatorBounds.size
                        }
                    } else {
                        val firstVisibleItem = listState.layoutInfo.visibleItemsInfo.first { it.index == animatedSelectedSetIndex.toInt() }
                        firstVisibleItem.offset + (animatedSelectedSetIndex - floor(animatedSelectedSetIndex)) * firstVisibleItem.size + listState.layoutInfo.beforeContentPadding
                    }

                    val selectorIndicatorLeftOffset = if (layoutDirection == LayoutDirection.Ltr) {
                        selectorIndicatorOffset
                    } else {
                        size.width - selectorIndicatorOffset - selectorWidthAnimation.value
                    }

                    drawRoundRect(
                        color = selectorIndicatorColor,
                        alpha = selectorIndicatorAlpha,
                        topLeft = Offset(selectorIndicatorLeftOffset, 0f),
                        size = size.copy(width = selectorWidthAnimation.value),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                }

                drawContent()
            },
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        items(sets) { set ->
            MultiSetTab(
                set = set,
                selected = set.setId == selectedSetId,
                onTabClick = { onSetSelected(set.setId) },
            )
        }
    }
}

@Composable
private fun MultiSetTab(
    modifier: Modifier = Modifier,
    set: AchievementSetUiModel,
    selected: Boolean,
    onTabClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .selectable(
                selected = selected,
                onClick = onTabClick,
            )
            .padding(horizontal = 32.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.CenterHorizontally),
    ) {
        if (LocalInspectionMode.current) {
            Box(Modifier.size(24.dp).background(Color.Gray))
        } else {
            AsyncImage(
                modifier = Modifier.size(24.dp),
                model = ImageRequest.Builder(LocalContext.current)
                    .data(set.setIcon.toString())
                    .crossfade(true)
                    .build(),
                contentDescription = null,
            )
        }

        Text(
            text = set.setTitle ?: stringResource(R.string.ra_base_set),
            style = MaterialTheme.typography.body2,
            color = if (selected) MaterialTheme.colors.onSecondary else MaterialTheme.colors.onSurface,
            lineHeight = 16.sp,
            maxLines = 2,
        )
    }
}