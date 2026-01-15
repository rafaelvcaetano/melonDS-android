package me.magnum.melonds.ui.emulator.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import me.magnum.melonds.R
import me.magnum.melonds.extensions.removeFirst
import me.magnum.melonds.ui.common.MelonPreviewSet
import me.magnum.melonds.ui.emulator.EmulatorViewModel
import me.magnum.melonds.ui.emulator.model.PopupEvent
import me.magnum.melonds.ui.emulator.model.RAEventUi
import me.magnum.melonds.ui.emulator.ui.AchievementInfo.AchievementPrimed
import me.magnum.melonds.ui.emulator.ui.AchievementInfo.AchievementProgress
import me.magnum.melonds.ui.romdetails.ui.preview.mockRAAchievementPreview
import me.magnum.melonds.ui.theme.MelonTheme
import me.magnum.rcheevosapi.model.RAAchievement
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Composable
fun AchievementUpdatesUi(
    viewModel: EmulatorViewModel,
) {
    val popupEventFlow = remember(viewModel) {
        val achievementsFlow = viewModel.achievementsEvent.mapNotNull {
            when (it) {
                is RAEventUi.AchievementTriggered -> PopupEvent.AchievementUnlockPopup(it.achievement)
                is RAEventUi.GameMastered -> PopupEvent.GameMasteredPopup(it)
                else -> null
            }
        }
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
        val modifier = Modifier
            .align(Alignment.TopCenter)
            .offset {
                val y = (popupOffset * (popupHeight ?: Int.MAX_VALUE)).dp
                IntOffset(0, y.roundToPx())
            }
            .onSizeChanged { popupHeight = it.height }

        when (currentPopupEvent) {
            is PopupEvent.AchievementUnlockPopup -> {
                AchievementPopupUi(
                    modifier = modifier,
                    achievement = currentPopupEvent.achievement,
                )
            }
            is PopupEvent.RAIntegrationPopup -> {
                RAIntegrationEventUi(
                    modifier = modifier,
                    event = currentPopupEvent.event,
                )
            }
            is PopupEvent.GameMasteredPopup ->{
                GameMasteredPopupUi(
                    modifier = modifier,
                    masteryEvent = currentPopupEvent.event,
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
    val listState = remember { AchievementUpdatesListState() }

    LaunchedEffect(achievementEventFlow) {
        achievementEventFlow.collect { event ->
            listState.handleEvent(event)
        }
    }

    LazyColumn(modifier) {
        items(
            items = listState.visibleInfos,
            key = {
                when (it) {
                    is AchievementPrimed -> "primed-${it.achievement.id}"
                    is AchievementProgress -> "progress-${(it.achievement.id)}"
                }
            }
        ) { info ->
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

private class AchievementUpdatesListState {

    val visibleInfos = mutableStateListOf<AchievementInfo>()

    fun handleEvent(event: RAEventUi) {
        when (event) {
            RAEventUi.Reset -> handleReset()
            is RAEventUi.AchievementPrimed -> handleAchievementPrimed(event)
            is RAEventUi.AchievementUnPrimed -> handleAchievementUnPrimed(event)
            is RAEventUi.AchievementProgressUpdated -> handleProgressUpdated(event)
            is RAEventUi.AchievementTriggered -> { /* no-op */ }
            is RAEventUi.GameMastered -> { /* no-op */ }
        }
    }

    private fun handleReset() {
        visibleInfos.forEach {
            it.state.dismiss()
        }
    }

    private fun handleAchievementPrimed(event: RAEventUi.AchievementPrimed) {
        val state = AchievementInfoState {
            visibleInfos.removeFirst { (it as? AchievementPrimed)?.achievement?.id == event.achievement.id }
        }
        visibleInfos.add(0, AchievementPrimed(event.achievement, state))
    }

    private fun handleAchievementUnPrimed(event: RAEventUi.AchievementUnPrimed) {
        val primedInfo = visibleInfos.firstOrNull { (it as? AchievementPrimed)?.achievement?.id == event.achievement.id }
        primedInfo?.state?.dismiss()
    }

    private fun handleProgressUpdated(event: RAEventUi.AchievementProgressUpdated) {
        val existingProgressIndex = visibleInfos.indexOfFirst { it is AchievementProgress }

        if (existingProgressIndex != -1) {
            handleExistingProgress(existingProgressIndex, event)
        } else {
            addNewProgress(event)
        }
    }

    private fun handleExistingProgress(
        existingIndex: Int,
        event: RAEventUi.AchievementProgressUpdated
    ) {
        val existingProgress = visibleInfos[existingIndex] as AchievementProgress
        
        when {
            existingProgress.achievement.id == event.achievement.id -> {
                // Update existing progress for same achievement
                visibleInfos[existingIndex] = existingProgress.copy(
                    achievement = event.achievement,
                    current = event.current,
                    target = event.target,
                    progress = event.progress,
                )
            }
            shouldReplaceProgress(existingProgress, event) -> {
                // Replace with new achievement that's closer to completion
                existingProgress.state.dismiss()
                addNewProgress(event)
            }
        }
    }

    private fun shouldReplaceProgress(
        existing: AchievementProgress,
        newEvent: RAEventUi.AchievementProgressUpdated
    ): Boolean {
        val newRelativeProgress = newEvent.current.toFloat() / newEvent.target
        return newRelativeProgress > existing.relativeProgress()
    }

    private fun addNewProgress(event: RAEventUi.AchievementProgressUpdated) {
        val state = AchievementInfoState {
            visibleInfos.removeFirst { (it as? AchievementProgress)?.achievement?.id == event.achievement.id }
        }
        val progressInfo = AchievementProgress(
            achievement = event.achievement,
            current = event.current,
            target = event.target,
            progress = event.progress,
            state = state,
        )
        visibleInfos.add(0, progressInfo)
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
        // Check if it was dismissed before it was even shown
        if (state.dismissed) {
            state.notifyDismissed()
        } else {
            state.show()
        }
    }

    AnimatedVisibility(
        visibleState = state.visibility,
        enter = fadeIn() + slideInVertically() + expandVertically(clip = false, expandFrom = Alignment.CenterVertically),
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
            delay(500.milliseconds)
            isDescriptionVisible = true
            delay(3.seconds)
            isDescriptionVisible = false
            alphaTransition.animateTo(0.5f)
        }

        AnimatedVisibility(isDescriptionVisible) {
            Column(Modifier.padding(start = 4.dp)) {
                Text(
                    text = stringResource(R.string.challenge_started),
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

    abstract val state: AchievementInfoState
    
    data class AchievementPrimed(val achievement: RAAchievement, override val state: AchievementInfoState) : AchievementInfo()

    data class AchievementProgress(
        val achievement: RAAchievement,
        val current: Int,
        val target: Int,
        val progress: String,
        override val state: AchievementInfoState
    ) : AchievementInfo() {
        fun relativeProgress() = current.toFloat() / target
    }
}

private class AchievementInfoState(private val onDismiss: () -> Unit) {
    val visibility = MutableTransitionState(false)
    var dismissed by mutableStateOf(false)
        private set

    fun show() {
        visibility.targetState = true
    }

    fun dismiss() {
        dismissed = true
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
            state = AchievementInfoState { },
        )
    }
}