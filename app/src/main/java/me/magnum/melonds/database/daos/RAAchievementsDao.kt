package me.magnum.melonds.database.daos

import androidx.room.*
import me.magnum.melonds.database.entities.retroachievements.RAAchievementEntity
import me.magnum.melonds.database.entities.retroachievements.RAGameSetMetadata
import me.magnum.melonds.database.entities.retroachievements.RAUserAchievementEntity

@Dao
interface RAAchievementsDao {

    @Query("SELECT * FROM ra_game_set_metadata WHERE game_id = :gameId")
    suspend fun getGameSetMetadata(gameId: Long): RAGameSetMetadata?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateGameSetMetadata(gameSetMetadata: RAGameSetMetadata)

    @Query("SELECT * FROM ra_achievement WHERE game_id = :gameId")
    suspend fun getGameAchievements(gameId: Long): List<RAAchievementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateGameAchievements(achievements: List<RAAchievementEntity>)

    @Query("SELECT * FROM ra_user_achievement WHERE game_id = :gameId AND is_unlocked = 1")
    suspend fun getGameUserUnlockedAchievements(gameId: Long): List<RAUserAchievementEntity>

    @Query("DELETE FROM ra_user_achievement WHERE game_id = :gameId")
    suspend fun deleteGameUserUnlockedAchievements(gameId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGameUserUnlockedAchievements(userAchievements: List<RAUserAchievementEntity>)

    @Transaction
    suspend fun updateGameUserUnlockedAchievements(gameId: Long, userAchievements: List<RAUserAchievementEntity>) {
        deleteGameUserUnlockedAchievements(gameId)
        insertGameUserUnlockedAchievements(userAchievements)
    }
}