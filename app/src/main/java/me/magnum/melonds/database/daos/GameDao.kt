package me.magnum.melonds.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.magnum.melonds.database.entities.CheatFolderWithCheats
import me.magnum.melonds.database.entities.GameEntity

@Dao
interface GameDao {
    @Query("SELECT * FROM game")
    fun getGames(): Flow<List<GameEntity>>

    @Query("SELECT * FROM game WHERE game_code = :gameCode AND (game_checksum IS NULL OR game_checksum = :gameChecksum)")
    suspend fun findGames(gameCode: String, gameChecksum: String): List<GameEntity>

    @Transaction
    @Query("SELECT * FROM cheat_folder WHERE game_id = :gameId")
    suspend fun getGameCheats(gameId: Long): List<CheatFolderWithCheats>

    @Insert
    fun insertGame(game: GameEntity): Long

    @Query("DELETE FROM game")
    fun deleteAll()
}