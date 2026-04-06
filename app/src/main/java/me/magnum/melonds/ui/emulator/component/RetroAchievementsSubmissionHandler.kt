package me.magnum.melonds.ui.emulator.component

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import me.magnum.melonds.domain.repositories.RetroAchievementsRepository
import me.magnum.melonds.domain.repositories.RomsRepository
import me.magnum.melonds.impl.emulator.EmulatorSession
import me.magnum.melonds.impl.network.NetworkConnectivityObserver
import me.magnum.melonds.ui.emulator.model.RAEventUi
import me.magnum.rcheevosapi.model.RAAchievement
import me.magnum.rcheevosapi.model.RAAchievementSet
import me.magnum.rcheevosapi.model.RALeaderboard
import me.magnum.rcheevosapi.model.RASetId
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

@OptIn(FlowPreview::class)
class RetroAchievementsSubmissionHandler @Inject constructor(
    private val retroAchievementsRepository: RetroAchievementsRepository,
    private val romsRepository: RomsRepository,
    private val emulatorSession: EmulatorSession,
    private val networkConnectivityObserver: NetworkConnectivityObserver,
) {

    private var pendingSubmissionsChannel: Channel<Unit>? = null
    private val pendingSubmissions = MutableStateFlow<List<PendingSubmission>>(emptyList())
    private val forceRetryEvent = Channel<Unit>(Channel.CONFLATED)

    fun startEmulatorSession(): Flow<RAEventUi> {
        val submissionsChannel = Channel<Unit>(Channel.CONFLATED)

        pendingSubmissionsChannel = submissionsChannel
        return submissionFlow(submissionsChannel).onCompletion {
            pendingSubmissions.value = emptyList()
            pendingSubmissionsChannel?.cancel()
            pendingSubmissionsChannel = null
        }
    }

    fun getPendingAchievementsFlow(): Flow<List<RAAchievement>> {
        return pendingSubmissions.map {
            it.filterIsInstance<PendingSubmission.AchievementSubmission>().map { it.achievement }
        }
    }

    fun hasPendingSubmissions(): Boolean {
        return pendingSubmissions.value.isNotEmpty()
    }

    fun retrySubmissionsImmediately() {
        forceRetryEvent.trySend(Unit)
    }

    fun getPendingSubmissionsSummaryFlow(): Flow<PendingSubmissionsSummary> {
        return pendingSubmissions.map {
            it.groupBy { it::class }.let {
                PendingSubmissionsSummary(
                    pendingAchievements = it[PendingSubmission.AchievementSubmission::class]?.count() ?: 0,
                    pendingLeaderboardSubmissions = it[PendingSubmission.LeaderboardEntrySubmission::class]?.count() ?: 0,
                )
            }
        }
    }

    fun addPendingAchievementSubmission(achievement: RAAchievement, forHardcoreMode: Boolean) {
        pendingSubmissions.update {
            it + PendingSubmission.AchievementSubmission(achievement, forHardcoreMode, true)
        }
        pendingSubmissionsChannel?.trySend(Unit)
    }

    fun addPendingLeaderboardSubmission(leaderboard: RALeaderboard, value: Int) {
        pendingSubmissions.update {
            it + PendingSubmission.LeaderboardEntrySubmission(leaderboard, value, true)
        }
        pendingSubmissionsChannel?.trySend(Unit)
    }

    private fun submissionFlow(submissionsChannel: Channel<Unit>): Flow<RAEventUi> {
        return flow {
            while (true) {
                // Wait for wake-up signal
                submissionsChannel.receive()
                while (pendingSubmissions.value.isNotEmpty()) {
                    val submission = pendingSubmissions.value.first()

                    val submissionResult = when (submission) {
                        is PendingSubmission.AchievementSubmission -> awardAchievement(submission)
                        is PendingSubmission.LeaderboardEntrySubmission -> submitLeaderboardEntry(submission)
                    }

                    submissionResult.uiEvents.forEach {
                        emit(it)
                    }

                    if (submissionResult is PendingSubmissionResult.Failure) {
                        // Submission failed. Re-add the submission to be retried
                        pendingSubmissions.update {
                            it - submission + submissionResult.nextSubmissionAttempt
                        }

                        // Wait for a connectivity switch from disconnected to connected. A switch is required instead of simply waiting for a connected state because there
                        // may be scenarios where the user is actually connected to the internet but submissions are failing (e.g.: RetroAchievements' servers are down) and we
                        // don't want to be constantly retrying
                        val connectivitySwitchFlow = networkConnectivityObserver.networkState.dropWhile { it == NetworkConnectivityObserver.NetworkState.CONNECTED }
                            .dropWhile { it == NetworkConnectivityObserver.NetworkState.DISCONNECTED }

                        merge(connectivitySwitchFlow, forceRetryEvent.receiveAsFlow())
                            .timeout(2.minutes)
                            .catch {
                                if (it !is TimeoutCancellationException) {
                                    throw it
                                }
                            }
                            .firstOrNull()
                    } else {
                        val updatedSubmissions = pendingSubmissions.updateAndGet { it - submission }
                        if (updatedSubmissions.isEmpty()) {
                            emit(RAEventUi.PendingDataSubmitted)
                        }
                    }
                }
            }
        }
    }

    private suspend fun awardAchievement(achievementSubmission: PendingSubmission.AchievementSubmission): PendingSubmissionResult {
        return retroAchievementsRepository.awardAchievement(achievementSubmission.achievement, achievementSubmission.forHardcoreMode).fold(
            onSuccess = {
                val events = if (it.achievementAwarded) {
                    buildList {
                        add(RAEventUi.AchievementTriggered(achievementSubmission.achievement))
                        if (it.isSetMastered()) {
                            buildSetMasteryEvent(achievementSubmission.achievement.setId, achievementSubmission.forHardcoreMode)?.let {
                                add(it)
                            }
                        }
                    }
                } else {
                    emptyList()
                }
                PendingSubmissionResult.Success(events)
            },
            onFailure = {
                val events = if (achievementSubmission.firstTry) {
                    listOf(RAEventUi.AchievementTriggerError(achievementSubmission.achievement))
                } else {
                    emptyList()
                }
                PendingSubmissionResult.Failure(achievementSubmission.copy(firstTry = false), events)
            }
        )
    }

    private suspend fun submitLeaderboardEntry(leaderboardSubmission: PendingSubmission.LeaderboardEntrySubmission): PendingSubmissionResult {
        return retroAchievementsRepository.submitLeaderboardEntry(leaderboardSubmission.leaderboard.id, leaderboardSubmission.value).fold(
            onSuccess = { submissionResponse ->
                val events = retroAchievementsRepository.getAchievementSetSummary(leaderboardSubmission.leaderboard.setId)?.let { setSummary ->
                    val submissionEvent = RAEventUi.LeaderboardEntrySubmitted(
                        leaderboardId = leaderboardSubmission.leaderboard.id,
                        title = leaderboardSubmission.leaderboard.title,
                        gameIcon = setSummary.iconUrl,
                        formattedScore = submissionResponse.formattedScore,
                        rank = submissionResponse.rank,
                        numberOfEntries = submissionResponse.numEntries,
                    )
                    listOf(submissionEvent)
                }.orEmpty()
                PendingSubmissionResult.Success(events)
            },
            onFailure = {
                val events = if (leaderboardSubmission.firstTry) {
                    val errorEvent = RAEventUi.LeaderboardEntrySubmitError(leaderboardSubmission.leaderboard.id)
                    listOf(errorEvent)
                } else {
                    emptyList()
                }
                PendingSubmissionResult.Failure(leaderboardSubmission.copy(firstTry = false), events)
            },
        )
    }

    private suspend fun buildSetMasteryEvent(setId: RASetId, forHardcoreMode: Boolean): RAEventUi.GameMastered? {
        val rom = (emulatorSession.currentSessionType() as? EmulatorSession.SessionType.RomSession)?.rom
        if (rom != null) {
            val setSummary = retroAchievementsRepository.getAchievementSetSummary(setId)
            val raUserName = retroAchievementsRepository.getUserAuthentication()?.username
            val romPlayTime = romsRepository.getRomAtUri(rom.uri)?.totalPlayTime

            if (setSummary != null) {
                val title = if (setSummary.type == RAAchievementSet.Type.Core) {
                    val gameSummary = retroAchievementsRepository.getGameSummary(rom.retroAchievementsHash)
                    gameSummary?.title.orEmpty()
                } else {
                    setSummary.title.orEmpty()
                }

                return RAEventUi.GameMastered(
                    gameTitle = title,
                    gameIcon = setSummary.iconUrl,
                    userName = raUserName,
                    playTime = romPlayTime,
                    forHardcodeMode = forHardcoreMode,
                )
            }
        }

        return null
    }

    data class PendingSubmissionsSummary(
        val pendingAchievements: Int,
        val pendingLeaderboardSubmissions: Int,
    ) {

        fun hasPendingSubmissions(): Boolean {
            return pendingAchievements > 0 || pendingLeaderboardSubmissions > 0
        }
    }

    private sealed class PendingSubmission {
        data class AchievementSubmission(val achievement: RAAchievement, val forHardcoreMode: Boolean, val firstTry: Boolean) : PendingSubmission()
        data class LeaderboardEntrySubmission(val leaderboard: RALeaderboard, val value: Int, val firstTry: Boolean) : PendingSubmission()
    }

    private sealed class PendingSubmissionResult(open val uiEvents: List<RAEventUi>) {
        data class Success(override val uiEvents: List<RAEventUi>) : PendingSubmissionResult(uiEvents)
        data class Failure(val nextSubmissionAttempt: PendingSubmission, override val uiEvents: List<RAEventUi> = emptyList()) : PendingSubmissionResult(uiEvents)
    }
}