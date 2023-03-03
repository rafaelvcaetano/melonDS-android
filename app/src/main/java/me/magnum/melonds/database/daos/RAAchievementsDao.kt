package me.magnum.melonds.database.daos

import androidx.room.*
import me.magnum.melonds.database.entities.retroachievements.*

@Dao
interface RAAchievementsDao {

    @Query("SELECT * FROM ra_game_set_metadata WHERE game_id = :gameId")
    suspend fun getGameSetMetadata(gameId: Long): RAGameSetMetadata?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateGameSetMetadata(gameSetMetadata: RAGameSetMetadata)

    @Query("SELECT * FROM ra_achievement WHERE game_id = :gameId")
    suspend fun getGameAchievements(gameId: Long): List<RAAchievementEntity>

    @Query("DELETE FROM ra_achievement WHERE game_id = :gameId")
    suspend fun deleteGameAchievements(gameId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGameAchievements(achievements: List<RAAchievementEntity>)

    @Upsert
    suspend fun updateGameData(gameData: RAGameEntity)

    @Transaction
    suspend fun updateGameData(gameId: Long, achievements: List<RAAchievementEntity>, richPresencePatch: String?) {
        deleteGameAchievements(gameId)
        insertGameAchievements(achievements)

        val gameEntity = RAGameEntity(gameId, richPresencePatch)
        updateGameData(gameEntity)
    }

    @Query("SELECT * FROM ra_game WHERE game_id = :gameId")
    suspend fun getGame(gameId: Long): RAGameEntity?

    @Query("SELECT * FROM ra_user_achievement WHERE game_id = :gameId AND is_unlocked = 1")
    suspend fun getGameUserUnlockedAchievements(gameId: Long): List<RAUserAchievementEntity>

    @Query("DELETE FROM ra_user_achievement WHERE game_id = :gameId")
    suspend fun deleteGameUserUnlockedAchievements(gameId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addUserAchievement(userAchievement: RAUserAchievementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGameUserUnlockedAchievements(userAchievements: List<RAUserAchievementEntity>)

    @Transaction
    suspend fun updateGameUserUnlockedAchievements(gameId: Long, userAchievements: List<RAUserAchievementEntity>) {
        deleteGameUserUnlockedAchievements(gameId)
        insertGameUserUnlockedAchievements(userAchievements)
    }

    @Query("SELECT * FROM ra_achievement WHERE id = :achievementId")
    suspend fun getAchievement(achievementId: Long): RAAchievementEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addPendingAchievementSubmission(pendingAchievementSubmission: RAPendingAchievementSubmissionEntity)

    @Query("SELECT * FROM ra_pending_achievement_award")
    suspend fun getPendingAchievementSubmissions(): List<RAPendingAchievementSubmissionEntity>

    @Delete
    suspend fun removePendingAchievementSubmission(pendingAchievementSubmission: RAPendingAchievementSubmissionEntity)

    @Query("DELETE FROM ra_game_hash_library")
    suspend fun deleteGameHashLibrary()

    @Insert
    suspend fun insertGameHashLibrary(hashLibrary: List<RAGameHashEntity>)

    @Query("SELECT * FROM ra_game_hash_library WHERE game_hash = :gameHash")
    suspend fun getGameHashEntity(gameHash: String): RAGameHashEntity?

    @Transaction
    suspend fun updateGameHashLibrary(hashLibrary: List<RAGameHashEntity>) {
        deleteGameHashLibrary()
        insertGameHashLibrary(hashLibrary)
    }
}