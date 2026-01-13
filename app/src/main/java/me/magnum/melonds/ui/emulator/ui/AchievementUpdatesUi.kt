package me.magnum.melonds.ui.emulator.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import me.magnum.melonds.extensions.removeFirst
import me.magnum.melonds.ui.common.MelonPreviewSet
import me.magnum.melonds.ui.emulator.EmulatorViewModel
import me.magnum.melonds.ui.emulator.model.PopupEvent
import me.magnum.melonds.ui.emulator.model.RAEventUi
import me.magnum.melonds.ui.emulator.ui.AchievementInfo.*
import me.magnum.melonds.ui.romdetails.ui.preview.mockRAAchievementPreview
import me.magnum.melonds.ui.theme.MelonTheme
import me.magnum.rcheevosapi.model.RAAchievement
import kotlin.time.Duration.Companion.seconds

@Composable
fun AchievementUpdatesUi(
    viewModel: EmulatorViewModel,
) {
    val popupEventFlow = remember(viewModel) {
        val achievementsFlow = viewModel.achievementsEvent.filterIsInstance<RAEventUi.AchievementTriggered>().map { PopupEvent.AchievementUnlockPopup(it.achievement) }
        val integrationFlow = viewModel.integrationEvent.map { PopupEvent.RAIntegrationPopup(it) }
        merge(achievementsFlow, integrationFlow)
    }

    Box(Modifier.fillMaxWidth()) {
        AchievementUpdatesList(
            modifier = Modifier.align(Alignment.TopStart).wrapContentSize(),
            achievementEventFlow = viewModel.achievementsEvent.filter { it !is RAEventUi.AchievementTriggered },
        )

        MainAchievementPopup(
            modifier = Modifier.fillMaxWidth(),
            popupEventFlow = popupEventFlow,
        )
    }
}

@Composable
private fun MainAchievementPopup(
    modifier: Modifier = Modifier,
    popupEventFlow: Flow<PopupEvent>,
) {
    var popupEvent by remember {
        mutableStateOf<PopupEvent?>(null)
    }
    var popupOffset by remember {
        mutableFloatStateOf(-1f)
    }
    var popupHeight by remember {
        mutableStateOf<Int?>(null)
    }

    LaunchedEffect(popupEventFlow) {
        popupEventFlow.collect {
            popupEvent = it
            animate(
                initialValue = -1f,
                targetValue = 0f,
                animationSpec = tween(easing = LinearEasing),
            ) { value, _ ->
                popupOffset = value
            }
            delay(5500)
            animate(
                initialValue = 0f,
                targetValue = -1f,
                animationSpec = tween(easing = LinearEasing),
            ) { value, _ ->
                popupOffset = value
            }
            popupEvent = null
        }
    }

    Box(modifier) {
        val currentPopupEvent = popupEvent
        when (currentPopupEvent) {
            is PopupEvent.AchievementUnlockPopup -> {
                AchievementPopupUi(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset {
                            val y = (popupOffset * (popupHeight ?: Int.MAX_VALUE)).dp
                            IntOffset(0, y.roundToPx())
                        }
                        .onSizeChanged { popupHeight = it.height },
                    achievement = currentPopupEvent.achievement,
                )
            }
            is PopupEvent.RAIntegrationPopup -> {
                RAIntegrationEventUi(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset {
                            val y = (popupOffset * (popupHeight ?: Int.MAX_VALUE)).dp
                            IntOffset(0, y.roundToPx())
                        }
                        .onSizeChanged { popupHeight = it.height },
                    event = currentPopupEvent.event,
                )
            }
            null -> {
                // Do nothing
            }
        }
    }
}

@Composable
private fun AchievementUpdatesList(
    modifier: Modifier = Modifier,
    achievementEventFlow: Flow<RAEventUi>,
) {
    val visibleAchievementInfos = remember {
        mutableStateListOf<AchievementInfo>()
    }

    LaunchedEffect(achievementEventFlow) {
        achievementEventFlow.collect { event ->
            when (event) {
                RAEventUi.Reset -> {
                    visibleAchievementInfos.forEach {
                        val state = when (it) {
                            is AchievementPrimed -> it.state
                            is AchievementProgress -> it.state
                        }
                        state.dismiss()
                    }
                }
                is RAEventUi.AchievementPrimed -> {
                    val achievementInfoState = rememberAchievementInfoState {
                        visibleAchievementInfos.removeFirst { (it as? AchievementPrimed)?.achievement?.id == event.achievement.id }
                    }

                    visibleAchievementInfos.add(0, AchievementPrimed(event.achievement, achievementInfoState))
                }
                is RAEventUi.AchievementUnPrimed -> {
                    val primedInfo = visibleAchievementInfos.firstOrNull { (it as? AchievementPrimed)?.achievement?.id == event.achievement.id } as? AchievementPrimed
                    primedInfo?.state?.dismiss()
                }
                is RAEventUi.AchievementProgressUpdated -> {
                    val existingProgressIndex = visibleAchievementInfos.indexOfFirst { (it as? AchievementProgress)?.achievement?.id == event.achievement.id }
                    if (existingProgressIndex != -1) {
                        val existingProgressInfo = visibleAchievementInfos[existingProgressIndex] as AchievementProgress
                        visibleAchievementInfos[existingProgressIndex] = AchievementInfo.AchievementProgress(event.achievement, event.progress, existingProgressInfo.state)
                    } else {
                        val achievementInfoState = rememberAchievementInfoState {
                            visibleAchievementInfos.removeFirst { (it as? AchievementProgress)?.achievement?.id == event.achievement.id }
                        }

                        visibleAchievementInfos.add(0, AchievementInfo.AchievementProgress(event.achievement, event.progress, achievementInfoState))
                    }
                }
                is RAEventUi.AchievementTriggered -> { /* no-op */}
            }
        }
    }

    LazyColumn(modifier) {
        items(
            count = visibleAchievementInfos.size,
            key = {
                val item = visibleAchievementInfos[it]
                when (item) {
                    is AchievementPrimed -> "primed-${item.achievement.id}"
                    is AchievementProgress -> "progress-${(item.achievement.id)}"
                }
            }
        ) {
            val info = visibleAchievementInfos[it]
            when (info) {
                is AchievementPrimed -> PrimedAchievement(info.achievement, info.state)
                is AchievementProgress -> {
                    AchievementProgress(
                        achievement = info.achievement,
                        progress = info.progress,
                        state = info.state,
                    )
                }
            }
        }
    }
}

@Composable
private fun AchievementInfo(
    modifier: Modifier = Modifier,
    achievement: RAAchievement,
    state: AchievementInfoState,
    body: (@Composable RowScope.() -> Unit)? = null,
) {
    LaunchedEffect(Unit) {
        state.show()
    }

    AnimatedVisibility(
        visibleState = state.visibility,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutHorizontally(),
    ) {
        DisposableEffect(state) {
            onDispose {
                state.notifyDismissed()
            }
        }

        Card(
            modifier = modifier.shadow(4.dp, RoundedCornerShape(4.dp)),
            shape = RoundedCornerShape(4.dp),
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (LocalInspectionMode.current) {
                    Box(Modifier.size(32.dp).background(Color.Gray))
                } else {
                    AsyncImage(
                        modifier = Modifier.size(32.dp),
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(achievement.badgeUrlUnlocked.toString())
                            .crossfade(false)
                            .build(),
                        contentDescription = null,
                    )
                }

                body?.invoke(this)
            }
        }
    }
}

@Composable
private fun PrimedAchievement(
    achievement: RAAchievement,
    state: AchievementInfoState,
) {
    val alphaTransition = remember {
        Animatable(1f)
    }

    AchievementInfo(
        modifier = Modifier.padding(8.dp).graphicsLayer {
            alpha = alphaTransition.value
        },
        achievement = achievement,
        state = state,
    ) {
        var isDescriptionVisible by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            isDescriptionVisible = true
            delay(3.seconds)
            isDescriptionVisible = false
            alphaTransition.animateTo(0.5f)
        }

        AnimatedVisibility(isDescriptionVisible) {
            Column(Modifier.padding(start = 4.dp)) {
                Text(
                    text = "Challenge started",
                    style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.caption,
                )
            }
        }
    }
}

@Composable
private fun AchievementProgress(
    achievement: RAAchievement,
    progress: String,
    state: AchievementInfoState,
) {
    LaunchedEffect(progress) {
        delay(2.seconds)
        state.dismiss()
    }

    AchievementInfo(
        modifier = Modifier.padding(8.dp),
        achievement = achievement,
        state = state,
    ) {
        Text(
            modifier = Modifier.align(Alignment.CenterVertically),
            text = progress,
        )
    }
}

private sealed class AchievementInfo {
    data class AchievementPrimed(val achievement: RAAchievement, val state: AchievementInfoState) : AchievementInfo()
    data class AchievementProgress(val achievement: RAAchievement, val progress: String, val state: AchievementInfoState) : AchievementInfo()
}

private fun rememberAchievementInfoState(onDismiss: () -> Unit): AchievementInfoState {
    return AchievementInfoState(onDismiss)
}

private class AchievementInfoState(private val onDismiss: () -> Unit) {
    val visibility = MutableTransitionState(false)

    fun show() {
        visibility.targetState = true
    }

    fun dismiss() {
        visibility.targetState = false
    }

    fun notifyDismissed() {
        onDismiss()
    }
}

@MelonPreviewSet
@Composable
private fun PreviewPrimedAchievement() {
    MelonTheme {
        PrimedAchievement(
            achievement = mockRAAchievementPreview(description = "Do the thing without taking damage"),
            state = rememberAchievementInfoState {  },
        )
    }
}