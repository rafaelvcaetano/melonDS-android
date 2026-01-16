package me.magnum.melonds.database.daos

import androidx.room.*
import me.magnum.melonds.database.entities.retroachievements.*

@Dao
abstract class RetroAchievementsDao {

    @Query("SELECT * FROM ra_game_set_metadata WHERE game_id = :gameId")
    abstract suspend fun getGameSetMetadata(gameId: Long): RAGameSetMetadata?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun updateGameSetMetadata(gameSetMetadata: RAGameSetMetadata)

    @Query("UPDATE ra_game_set_metadata SET last_user_data_updated = NULL, last_hardcore_user_data_updated = NULL")
    protected abstract suspend fun clearAllGameSetMetadataLastUserDataUpdate()

    @Query("SELECT * FROM ra_achievement WHERE game_id = :gameId")
    abstract suspend fun getGameAchievements(gameId: Long): List<RAAchievementEntity>

    @Query("SELECT * FROM ra_achievement WHERE id = :achievementId")
    abstract suspend fun getAchievement(achievementId: Long): RAAchievementEntity?

    @Query("DELETE FROM ra_achievement WHERE game_id = :gameId")
    protected abstract suspend fun deleteGameAchievements(gameId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertGameAchievements(achievements: List<RAAchievementEntity>)

    @Query("SELECT * FROM ra_leaderboard WHERE id = :leaderboardId")
    abstract suspend fun getLeaderboard(leaderboardId: Long): RALeaderboardEntity?

    @Query("SELECT * FROM ra_leaderboard WHERE game_id = :gameId")
    abstract suspend fun getGameLeaderboards(gameId: Long): List<RALeaderboardEntity>

    @Query("DELETE FROM ra_leaderboard WHERE game_id = :gameId")
    protected abstract suspend fun deleteGameLeaderboards(gameId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertGameLeaderboards(leaderboards: List<RALeaderboardEntity>)

    @Upsert
    protected abstract suspend fun updateGameData(gameData: RAGameEntity)

    @Transaction
    open suspend fun updateGameData(gameEntity: RAGameEntity, achievements: List<RAAchievementEntity>, leaderboards: List<RALeaderboardEntity>) {
        deleteGameAchievements(gameEntity.gameId)
        deleteGameLeaderboards(gameEntity.gameId)

        insertGameAchievements(achievements)
        insertGameLeaderboards(leaderboards)

        updateGameData(gameEntity)
    }

    @Query("SELECT * FROM ra_game WHERE game_id = :gameId")
    abstract suspend fun getGame(gameId: Long): RAGameEntity?

    @Query("SELECT * FROM ra_user_achievement WHERE game_id = :gameId AND is_hardcore = :forHardcoreMode AND is_unlocked = 1")
    abstract suspend fun getGameUserUnlockedAchievements(gameId: Long, forHardcoreMode: Boolean): List<RAUserAchievementEntity>

    @Query("DELETE FROM ra_user_achievement WHERE game_id = :gameId")
    protected abstract suspend fun deleteGameUserUnlockedAchievements(gameId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun addUserAchievement(userAchievement: RAUserAchievementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertGameUserUnlockedAchievements(userAchievements: List<RAUserAchievementEntity>)

    @Transaction
    open suspend fun updateGameUserUnlockedAchievements(gameId: Long, userAchievements: List<RAUserAchievementEntity>) {
        deleteGameUserUnlockedAchievements(gameId)
        insertGameUserUnlockedAchievements(userAchievements)
    }

    @Query("DELETE FROM ra_user_achievement")
    protected abstract suspend fun deleteAllUserUnlockedAchievements()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun addPendingAchievementSubmission(pendingAchievementSubmission: RAPendingAchievementSubmissionEntity)

    @Query("SELECT * FROM ra_pending_achievement_award")
    abstract suspend fun getPendingAchievementSubmissions(): List<RAPendingAchievementSubmissionEntity>

    @Delete
    abstract suspend fun removePendingAchievementSubmission(pendingAchievementSubmission: RAPendingAchievementSubmissionEntity)

    @Query("DELETE FROM ra_pending_achievement_award")
    protected abstract suspend fun deleteAllPendingAchievementSubmissions()

    @Query("DELETE FROM ra_game_hash_library")
    abstract suspend fun deleteGameHashLibrary()

    @Insert
    protected abstract suspend fun insertGameHashLibrary(hashLibrary: List<RAGameHashEntity>)

    @Query("SELECT * FROM ra_game_hash_library WHERE game_hash = :gameHash")
    abstract suspend fun getGameHashEntity(gameHash: String): RAGameHashEntity?

    @Transaction
    open suspend fun updateGameHashLibrary(hashLibrary: List<RAGameHashEntity>) {
        deleteGameHashLibrary()
        insertGameHashLibrary(hashLibrary)
    }

    @Transaction
    open suspend fun deleteAllAchievementUserData() {
        clearAllGameSetMetadataLastUserDataUpdate()
        deleteAllUserUnlockedAchievements()
        deleteAllPendingAchievementSubmissions()
    }
}