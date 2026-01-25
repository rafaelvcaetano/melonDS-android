package me.magnum.melonds.ui.emulator.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import me.magnum.melonds.extensions.removeFirst
import me.magnum.melonds.ui.emulator.EmulatorViewModel
import me.magnum.melonds.ui.emulator.model.PopupEvent
import me.magnum.melonds.ui.emulator.model.RAEventUi
import me.magnum.melonds.ui.emulator.ui.AchievementInfo.AchievementPrimed
import me.magnum.melonds.ui.emulator.ui.AchievementInfo.AchievementProgress
import me.magnum.melonds.ui.emulator.ui.info.AchievementInfoState
import me.magnum.melonds.ui.emulator.ui.info.AchievementProgressUi
import me.magnum.melonds.ui.emulator.ui.info.LeaderboardAttemptUi
import me.magnum.melonds.ui.emulator.ui.info.LeaderboardEntrySubmissionUi
import me.magnum.melonds.ui.emulator.ui.info.PrimedAchievementUi
import me.magnum.melonds.ui.emulator.ui.popup.AchievementPopupUi
import me.magnum.melonds.ui.emulator.ui.popup.GameMasteredPopupUi
import me.magnum.melonds.ui.emulator.ui.popup.RAIntegrationEventUi
import me.magnum.rcheevosapi.model.RAAchievement
import me.magnum.rcheevosapi.model.RALeaderboard
import java.net.URL

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
                    is AchievementProgress -> "progress-${it.achievement.id}"
                    is AchievementInfo.LeaderboardAttempt -> "leaderboard-attempt-${it.leaderboard.id}"
                    is AchievementInfo.LeaderboardEntrySubmitted -> "leaderboard-${it.leaderboardId}"
                }
            },
        ) { info ->
            when (info) {
                is AchievementPrimed -> PrimedAchievementUi(info)
                is AchievementProgress -> AchievementProgressUi(info)
                is AchievementInfo.LeaderboardAttempt -> LeaderboardAttemptUi(info)
                is AchievementInfo.LeaderboardEntrySubmitted -> LeaderboardEntrySubmissionUi(info)
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
            is RAEventUi.LeaderboardAttemptStarted -> handleLeaderboardAttemptStarted(event)
            is RAEventUi.LeaderboardAttemptUpdated -> handleLeaderboardAttemptUpdated(event)
            is RAEventUi.LeaderboardEntrySubmitted -> handleLeaderboardEntrySubmitted(event)
            is RAEventUi.LeaderboardAttemptCancelled -> handleLeaderboardAttemptCancelled(event)
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
        // Start by checking if there is an existing progress update for this exact achievement
        val exactAchievementProgressIndex = visibleInfos.indexOfFirst { (it as? AchievementProgress)?.achievement?.id == event.achievement.id  }

        if (exactAchievementProgressIndex != -1) {
            handleExistingProgress(exactAchievementProgressIndex, event)
        } else {
            // Check if there is ANY existing progress update
            val existingProgressIndex = visibleInfos.indexOfFirst { it is AchievementProgress }
            if (existingProgressIndex != -1) {
                handleExistingProgress(existingProgressIndex, event)
            } else {
                addNewProgress(event)
            }
        }
    }

    private fun handleLeaderboardAttemptStarted(event: RAEventUi.LeaderboardAttemptStarted) {
        val state = AchievementInfoState {
            visibleInfos.removeFirst { (it as? AchievementInfo.LeaderboardAttempt)?.leaderboard?.id == event.leaderboard.id }
        }
        visibleInfos.add(0, AchievementInfo.LeaderboardAttempt(event.leaderboard, event.gameIcon, "", state))
    }

    private fun handleLeaderboardAttemptUpdated(event: RAEventUi.LeaderboardAttemptUpdated) {
        val attemptIndex = visibleInfos.indexOfFirst {
            (it as? AchievementInfo.LeaderboardAttempt)?.leaderboard?.id == event.leaderboardId
        }

        if (attemptIndex != -1) {
            val existingAttempt = visibleInfos[attemptIndex] as AchievementInfo.LeaderboardAttempt
            visibleInfos[attemptIndex] = existingAttempt.copy(currentValue = event.formattedValue)
        }
    }

    private fun handleLeaderboardEntrySubmitted(event: RAEventUi.LeaderboardEntrySubmitted) {
        // Dismiss any existing leaderboard attempt before showing completion UI
        val existingAttempt = visibleInfos.firstOrNull { it is AchievementInfo.LeaderboardAttempt }
        existingAttempt?.state?.dismiss()

        val state = AchievementInfoState {
            visibleInfos.removeFirst { (it as? AchievementInfo.LeaderboardEntrySubmitted)?.title == event.title }
        }
        val info = AchievementInfo.LeaderboardEntrySubmitted(event.leaderboardId, event.title, event.gameIcon, event.formattedScore, event.rank, event.numberOfEntries, state)
        visibleInfos.add(0, info)
    }

    private fun handleLeaderboardAttemptCancelled(event: RAEventUi.LeaderboardAttemptCancelled) {
        val attempt = visibleInfos.firstOrNull {
            (it as? AchievementInfo.LeaderboardAttempt)?.leaderboard?.id == event.leaderboardId
        }
        attempt?.state?.dismiss()
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

internal sealed class AchievementInfo {

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

    data class LeaderboardAttempt(
        val leaderboard: RALeaderboard,
        val gameIcon: URL,
        val currentValue: String,
        override val state: AchievementInfoState,
    ) : AchievementInfo()

    data class LeaderboardEntrySubmitted(
        val leaderboardId: Long,
        val title: String,
        val gameIcon: URL,
        val formattedScore: String,
        val rank: Int,
        val numberOfEntries: Int,
        override val state: AchievementInfoState,
    ) : AchievementInfo()
}