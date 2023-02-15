package me.magnum.melonds.impl

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.work.*
import me.magnum.melonds.common.workers.RetroAchievementsSubmissionWorker
import me.magnum.melonds.database.daos.RAAchievementsDao
import me.magnum.melonds.database.entities.retroachievements.RAGameHashEntity
import me.magnum.melonds.database.entities.retroachievements.RAGameSetMetadata
import me.magnum.melonds.database.entities.retroachievements.RAPendingAchievementSubmissionEntity
import me.magnum.melonds.database.entities.retroachievements.RAUserAchievementEntity
import me.magnum.melonds.domain.model.retroachievements.RAUserAchievement
import me.magnum.melonds.domain.repositories.RetroAchievementsRepository
import me.magnum.melonds.impl.mappers.retroachievements.mapToEntity
import me.magnum.melonds.impl.mappers.retroachievements.mapToModel
import me.magnum.rcheevosapi.RAApi
import me.magnum.rcheevosapi.RAUserAuthStore
import me.magnum.rcheevosapi.model.RAAchievement
import me.magnum.rcheevosapi.model.RAGameId
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

    override suspend fun login(username: String, password: String): Result<Unit> {
        return raApi.login(username, password)
    }

    override suspend fun getGameUserAchievements(gameHash: String): Result<List<RAUserAchievement>> {
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

        val userUnlocksResult = fetchGameUserUnlockedAchievements(gameId, currentMetadata)
        if (userUnlocksResult.isFailure) {
            return Result.failure(userUnlocksResult.exceptionOrNull()!!)
        }

        val gameAchievements = gameAchievementsResult.getOrThrow()
        val userUnlocks = userUnlocksResult.getOrThrow()

        val userAchievements = gameAchievements.map {
            RAUserAchievement(
                achievement = it,
                isUnlocked = userUnlocks.contains(it.id),
            )
        }
        return Result.success(userAchievements)
    }

    override suspend fun getAchievement(achievementId: Long): Result<RAAchievement?> {
        return runCatching {
            achievementsDao.getAchievement(achievementId)
        }.map {
            it?.mapToModel()
        }
    }

    override suspend fun awardAchievement(achievement: RAAchievement) {
        submitAchievementAward(achievement.id, achievement.gameId, false, true)
    }

    override suspend fun submitPendingAchievements(): Result<Unit> {
        achievementsDao.getPendingAchievementSubmissions().forEach {
            // Do not schedule resubmission if this fails. The current submission job should schedule another attempt
            val submissionResult = submitAchievementAward(it.achievementId, RAGameId(it.gameId), it.forHardcoreMode, false)
            if (submissionResult.isFailure) {
                return submissionResult
            }

            achievementsDao.removePendingAchievementSubmission(it)
        }

        return Result.success(Unit)
    }

    private suspend fun submitAchievementAward(achievementId: Long, gameId: RAGameId, forHardcoreMode: Boolean, scheduleResubmissionOnFailure: Boolean): Result<Unit> {
        // Award the achievement immediately locally
        val userAchievement = RAUserAchievementEntity(gameId.id, achievementId, true)
        achievementsDao.addUserAchievement(userAchievement)

        return raApi.awardAchievement(achievementId, forHardcoreMode).onFailure {
            // On failure, insert it into the pending achievements to be re-submitted later
            val pendingAchievementSubmissionEntity = RAPendingAchievementSubmissionEntity(
                achievementId = achievementId,
                gameId = gameId.id,
                forHardcoreMode = forHardcoreMode,
            )
            achievementsDao.addPendingAchievementSubmission(pendingAchievementSubmissionEntity)
            if (scheduleResubmissionOnFailure) {
                scheduleAchievementSubmissionJob()
            }
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
        } else {
            runCatching {
                achievementsDao.getGameHashEntity(gameHash)
            }.map {
                it?.let {
                    RAGameId(it.gameId)
                }
            }
        }
    }

    private suspend fun fetchGameAchievements(gameId: RAGameId, gameSetMetadata: CurrentGameSetMetadata): Result<List<RAAchievement>> {
        return if (mustRefreshAchievementSet(gameSetMetadata.currentMetadata)) {
            raApi.getGameInfo(gameId).map { game ->
                game.achievements
            }.onSuccess { achievements ->
                val achievementEntities = achievements.map {
                    it.mapToEntity()
                }

                val newMetadata = gameSetMetadata.withNewAchievementSetUpdate()
                achievementsDao.updateGameAchievements(gameId.id, achievementEntities)
                achievementsDao.updateGameSetMetadata(newMetadata)
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

    private suspend fun fetchGameUserUnlockedAchievements(gameId: RAGameId, gameSetMetadata: CurrentGameSetMetadata): Result<List<Long>> {
        return if (mustRefreshUserData(gameSetMetadata.currentMetadata)) {
            raApi.getUserUnlockedAchievements(gameId, false).onSuccess { userUnlocks ->
                val userAchievementEntities = userUnlocks.map {
                    RAUserAchievementEntity(
                        gameId.id,
                        it,
                        true,
                    )
                }

                val newMetadata = gameSetMetadata.withNewUserAchievementsUpdate()
                achievementsDao.updateGameUserUnlockedAchievements(gameId.id, userAchievementEntities)
                achievementsDao.updateGameSetMetadata(newMetadata)
            }
        } else {
            runCatching {
                achievementsDao.getGameUserUnlockedAchievements(gameId.id).map {
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

    private fun mustRefreshUserData(gameSetMetadata: RAGameSetMetadata?): Boolean {
        if (gameSetMetadata?.lastUserDataUpdated == null) {
            return true
        }

        // Sync user achievement data once a day
        return Duration.between(gameSetMetadata.lastUserDataUpdated, Instant.now()) >= Duration.ofDays(1)
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
            return (currentMetadata?.copy(lastAchievementSetUpdated = Instant.now()) ?: RAGameSetMetadata(gameId.id, Instant.now(), null)).also {
                currentMetadata = it
            }
        }

        fun withNewUserAchievementsUpdate(): RAGameSetMetadata {
            return (currentMetadata?.copy(lastUserDataUpdated = Instant.now()) ?: RAGameSetMetadata(gameId.id, null, Instant.now())).also {
                currentMetadata = it
            }
        }
    }
}