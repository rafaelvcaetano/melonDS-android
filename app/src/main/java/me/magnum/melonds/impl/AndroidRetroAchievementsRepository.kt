package me.magnum.melonds.impl

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import me.magnum.melonds.common.workers.RetroAchievementsSubmissionWorker
import me.magnum.melonds.database.daos.RAAchievementsDao
import me.magnum.melonds.database.entities.retroachievements.RAGameEntity
import me.magnum.melonds.database.entities.retroachievements.RAGameHashEntity
import me.magnum.melonds.database.entities.retroachievements.RAGameSetMetadata
import me.magnum.melonds.database.entities.retroachievements.RAPendingAchievementSubmissionEntity
import me.magnum.melonds.database.entities.retroachievements.RAUserAchievementEntity
import me.magnum.melonds.domain.model.retroachievements.RAGameSummary
import me.magnum.melonds.domain.model.retroachievements.RAUserAchievement
import me.magnum.melonds.domain.model.retroachievements.exception.RAGameNotExist
import me.magnum.melonds.domain.repositories.RetroAchievementsRepository
import me.magnum.melonds.impl.mappers.retroachievements.mapToEntity
import me.magnum.melonds.impl.mappers.retroachievements.mapToModel
import me.magnum.rcheevosapi.RAApi
import me.magnum.rcheevosapi.RAUserAuthStore
import me.magnum.rcheevosapi.model.RAAchievement
import me.magnum.rcheevosapi.model.RAGameId
import me.magnum.rcheevosapi.model.RAUserAuth
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

class AndroidRetroAchievementsRepository(
    private val raApi: RAApi,
    private val achievementsDao: RAAchievementsDao,
    private val raUserAuthStore: RAUserAuthStore,
    private val sharedPreferences: SharedPreferences,
    private val context: Context,
) : RetroAchievementsRepository {

    private companion object {
        const val RA_HASH_LIBRARY_LAST_UPDATED = "ra_hash_library_last_updated"
        const val PENDING_ACHIEVEMENT_SUBMISSION_WORKER_NAME = "ra_pending_achievement_submission_worker"
    }

    override suspend fun isUserAuthenticated(): Boolean {
        return raUserAuthStore.getUserAuth() != null
    }

    override suspend fun getUserAuthentication(): RAUserAuth? {
        return raUserAuthStore.getUserAuth()
    }

    override suspend fun login(username: String, password: String): Result<Unit> {
        return raApi.login(username, password)
    }

    override suspend fun logout() {
        achievementsDao.deleteAllAchievementUserData()
        raUserAuthStore.clearUserAuth()
    }

    override suspend fun getGameUserAchievements(gameHash: String, forHardcoreMode: Boolean): Result<List<RAUserAchievement>> {
        val gameIdResult = getGameIdFromGameHash(gameHash)
        if (gameIdResult.isFailure) {
            return Result.failure(gameIdResult.exceptionOrNull()!!)
        }

        val gameId = gameIdResult.getOrThrow()
        if (gameId == null) {
            return Result.success(emptyList())
        }

        val gameSetMetadata = achievementsDao.getGameSetMetadata(gameId.id)
        val currentMetadata = CurrentGameSetMetadata(gameId, gameSetMetadata)

        val gameAchievementsResult = fetchGameAchievements(gameId, currentMetadata)
        if (gameAchievementsResult.isFailure) {
            return Result.failure(gameAchievementsResult.exceptionOrNull()!!)
        }

        val userUnlocksResult = fetchGameUserUnlockedAchievements(gameId, forHardcoreMode, currentMetadata)
        if (userUnlocksResult.isFailure) {
            return Result.failure(userUnlocksResult.exceptionOrNull()!!)
        }

        val gameAchievements = gameAchievementsResult.getOrThrow()
        val userUnlocks = userUnlocksResult.getOrThrow()

        val userAchievements = gameAchievements.map {
            RAUserAchievement(
                achievement = it,
                isUnlocked = userUnlocks.contains(it.id),
                forHardcoreMode = forHardcoreMode,
            )
        }
        return Result.success(userAchievements)
    }

    override suspend fun getGameSummary(gameHash: String): RAGameSummary? {
        val gameId = getGameIdFromGameHash(gameHash).getOrNull() ?: return null
        return achievementsDao.getGame(gameId.id)?.let {
            RAGameSummary(
                URL(it.icon),
                it.richPresencePatch,
            )
        }
    }

    override suspend fun getAchievement(achievementId: Long): Result<RAAchievement?> {
        return runCatching {
            achievementsDao.getAchievement(achievementId)
        }.map {
            it?.mapToModel()
        }
    }

    override suspend fun awardAchievement(achievement: RAAchievement, forHardcoreMode: Boolean): Result<Unit> {
        return submitAchievementAward(achievement.id, achievement.gameId, forHardcoreMode).onFailure {
            scheduleAchievementSubmissionJob()
        }
    }

    override suspend fun submitPendingAchievements(): Result<Unit> {
        achievementsDao.getPendingAchievementSubmissions().forEach {
            // Do not schedule resubmission if this fails. The current submission job should schedule another attempt
            val submissionResult = submitAchievementAward(it.achievementId, RAGameId(it.gameId), it.forHardcoreMode)
            if (submissionResult.isFailure) {
                return submissionResult
            }

            achievementsDao.removePendingAchievementSubmission(it)
        }

        return Result.success(Unit)
    }

    override suspend fun startSession(gameHash: String): Result<Unit> {
        val gameId = getGameIdFromGameHash(gameHash).getOrNull() ?: return Result.failure(RAGameNotExist(gameHash))
        return raApi.startSession(gameId)
    }

    override suspend fun sendSessionHeartbeat(gameHash: String, richPresenceDescription: String?) {
        val gameId = getGameIdFromGameHash(gameHash).getOrNull() ?: return
        raApi.sendPing(gameId, richPresenceDescription)
    }

    private suspend fun submitAchievementAward(achievementId: Long, gameId: RAGameId, forHardcoreMode: Boolean): Result<Unit> {
        // Award the achievement immediately locally
        val userAchievement = RAUserAchievementEntity(
            gameId = gameId.id,
            achievementId = achievementId,
            isUnlocked = true,
            isHardcore = forHardcoreMode,
        )
        achievementsDao.addUserAchievement(userAchievement)

        return raApi.awardAchievement(achievementId, forHardcoreMode).onFailure {
            // On failure, insert it into the pending achievements to be re-submitted later
            val pendingAchievementSubmissionEntity = RAPendingAchievementSubmissionEntity(
                achievementId = achievementId,
                gameId = gameId.id,
                forHardcoreMode = forHardcoreMode,
            )
            achievementsDao.addPendingAchievementSubmission(pendingAchievementSubmissionEntity)
        }
    }

    private suspend fun getGameIdFromGameHash(gameHash: String): Result<RAGameId?> {
        return if (mustRefreshHashLibrary()) {
            raApi.getGameHashList()
                .onSuccess { gameHashes ->
                    val gameHashEntities = gameHashes.map {
                        RAGameHashEntity(it.key, it.value.id)
                    }
                    achievementsDao.updateGameHashLibrary(gameHashEntities)
                    sharedPreferences.edit {
                        putLong(RA_HASH_LIBRARY_LAST_UPDATED, Instant.now().toEpochMilli())
                    }
                }
                .map {
                    it[gameHash]
                }
                .recoverCatching {
                    achievementsDao.getGameHashEntity(gameHash)?.let {
                        RAGameId(it.gameId)
                    }
                }
        } else {
            runCatching {
                achievementsDao.getGameHashEntity(gameHash)?.let {
                    RAGameId(it.gameId)
                }
            }
        }
    }

    private suspend fun fetchGameAchievements(gameId: RAGameId, gameSetMetadata: CurrentGameSetMetadata): Result<List<RAAchievement>> {
        return if (mustRefreshAchievementSet(gameSetMetadata.currentMetadata)) {
            raApi.getGameInfo(gameId).mapCatching { game ->
                val achievementEntities = game.achievements.map {
                    it.mapToEntity()
                }

                val gameEntity = RAGameEntity(game.id.id, game.richPresencePatch, game.icon.toString())
                val newMetadata = gameSetMetadata.withNewAchievementSetUpdate()
                achievementsDao.updateGameData(gameEntity, achievementEntities)
                achievementsDao.updateGameSetMetadata(newMetadata)
                game.achievements
            }.recoverCatching { exception ->
                if (gameSetMetadata.isGameAchievementDataKnown()) {
                    // Load DB data because we know that it was previously loaded
                    achievementsDao.getGameAchievements(gameId.id).map { it ->
                        it.mapToModel()
                    }
                } else {
                    // The achievement data has never been downloaded for this game. Rethrow exception
                    throw exception
                }
            }
        } else {
            runCatching {
                achievementsDao.getGameAchievements(gameId.id).map {
                    it.mapToModel()
                }
            }
        }.map { achievements ->
            achievements.filter { it.type == RAAchievement.Type.CORE }
        }
    }

    private suspend fun fetchGameUserUnlockedAchievements(gameId: RAGameId, forHardcoreMode: Boolean, gameSetMetadata: CurrentGameSetMetadata): Result<List<Long>> {
        return if (mustRefreshUserData(gameSetMetadata.currentMetadata, forHardcoreMode)) {
            raApi.getUserUnlockedAchievements(gameId, forHardcoreMode).onSuccess { userUnlocks ->
                val userAchievementEntities = userUnlocks.map {
                    RAUserAchievementEntity(
                        gameId = gameId.id,
                        achievementId = it,
                        isUnlocked = true,
                        isHardcore = forHardcoreMode,
                    )
                }

                val newMetadata = gameSetMetadata.withNewUserAchievementsUpdate(forHardcoreMode)
                achievementsDao.updateGameUserUnlockedAchievements(gameId.id, userAchievementEntities)
                achievementsDao.updateGameSetMetadata(newMetadata)
            }.recoverCatching { exception ->
                if (gameSetMetadata.isUserAchievementDataKnown(forHardcoreMode)) {
                    // Load DB data because we know that it was previously loaded
                    achievementsDao.getGameUserUnlockedAchievements(gameId.id, forHardcoreMode).map {
                        it.achievementId
                    }
                } else {
                    // The user's achievement data has never been downloaded for this game. Rethrow exception
                    throw exception
                }
            }
        } else {
            runCatching {
                achievementsDao.getGameUserUnlockedAchievements(gameId.id, forHardcoreMode).map {
                    it.achievementId
                }
            }
        }
    }

    private fun mustRefreshHashLibrary(): Boolean {
        val hashLibraryLastUpdateTimestamp = sharedPreferences.getLong(RA_HASH_LIBRARY_LAST_UPDATED, 0)
        val hashLibraryLastUpdate = Instant.ofEpochMilli(hashLibraryLastUpdateTimestamp)

        // Update the game hash library once a month
        return Duration.between(hashLibraryLastUpdate, Instant.now()) > Duration.ofDays(30)
    }

    private fun mustRefreshAchievementSet(gameSetMetadata: RAGameSetMetadata?): Boolean {
        if (gameSetMetadata?.lastAchievementSetUpdated == null) {
            return true
        }

        // Update the achievement set once a week
        return Duration.between(gameSetMetadata.lastAchievementSetUpdated, Instant.now()) >= Duration.ofDays(7)
    }

    private fun mustRefreshUserData(gameSetMetadata: RAGameSetMetadata?, forHardcoreMode: Boolean): Boolean {
        val lastUserDataUpdateTimestamp = if (forHardcoreMode) {
            gameSetMetadata?.lastHardcoreUserDataUpdated
        } else {
            gameSetMetadata?.lastSoftcoreUserDataUpdated
        }

        if (lastUserDataUpdateTimestamp == null) {
            return true
        }

        // Sync user achievement data once a day
        return Duration.between(lastUserDataUpdateTimestamp, Instant.now()) >= Duration.ofDays(1)
    }

    private fun scheduleAchievementSubmissionJob() {
        val workConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<RetroAchievementsSubmissionWorker>()
            .setConstraints(workConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 60, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(PENDING_ACHIEVEMENT_SUBMISSION_WORKER_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, workRequest)
    }

    private class CurrentGameSetMetadata(private val gameId: RAGameId, initialMetadata: RAGameSetMetadata?) {
        var currentMetadata = initialMetadata
            private set

        fun withNewAchievementSetUpdate(): RAGameSetMetadata {
            return (currentMetadata?.copy(lastAchievementSetUpdated = Instant.now()) ?: RAGameSetMetadata(gameId.id, Instant.now(), null, null)).also {
                currentMetadata = it
            }
        }

        fun withNewUserAchievementsUpdate(forHardcoreMode: Boolean): RAGameSetMetadata {
            return if (forHardcoreMode) {
                currentMetadata?.copy(lastHardcoreUserDataUpdated = Instant.now()) ?: RAGameSetMetadata(gameId.id, null, null, Instant.now()).also {
                    currentMetadata = it
                }
            } else {
                currentMetadata?.copy(lastSoftcoreUserDataUpdated = Instant.now()) ?: RAGameSetMetadata(gameId.id, null, Instant.now(), null).also {
                    currentMetadata = it
                }
            }
        }

        fun isGameAchievementDataKnown(): Boolean {
            return currentMetadata?.lastAchievementSetUpdated != null
        }

        fun isUserAchievementDataKnown(forHardcoreMode: Boolean): Boolean {
            return if (forHardcoreMode) {
                currentMetadata?.lastHardcoreUserDataUpdated != null
            } else {
                currentMetadata?.lastSoftcoreUserDataUpdated != null
            }
        }
    }
}