package me.magnum.melonds.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.magnum.melonds.database.entities.CheatFolderWithCheats
import me.magnum.melonds.database.entities.GameEntity

@Dao
interface GameDao {
    @Query("SELECT * FROM game")
    suspend fun getGames(): List<GameEntity>

    @Query("SELECT * FROM game WHERE id = :gameId")
    suspend fun getGame(gameId: Long): GameEntity?

    @Query("SELECT * FROM game WHERE game_code = :gameCode AND game_checksum = :gameChecksum")
    suspend fun findGame(gameCode: String, gameChecksum: String): GameEntity?

    @Transaction
    @Query("SELECT * FROM cheat_folder WHERE game_id = :gameId")
    fun getGameCheats(gameId: Long): Flow<List<CheatFolderWithCheats>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGame(game: GameEntity): Long

    @Query("DELETE FROM game WHERE id NOT IN (SELECT DISTINCT game_id FROM cheat_folder)")
    suspend fun deleteEmptyGames()
}