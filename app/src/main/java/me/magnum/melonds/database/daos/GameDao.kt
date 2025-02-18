package me.magnum.melonds.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import me.magnum.melonds.database.entities.CheatFolderWithCheats
import me.magnum.melonds.database.entities.GameEntity

@Dao
interface GameDao {
    @Query("SELECT * FROM game")
    suspend fun getGames(): List<GameEntity>

    @Query("SELECT * FROM game WHERE game_code = :gameCode AND game_checksum = :gameChecksum")
    suspend fun findGame(gameCode: String, gameChecksum: String): GameEntity?

    @Transaction
    @Query("SELECT * FROM cheat_folder WHERE game_id = :gameId")
    suspend fun getGameCheats(gameId: Long): List<CheatFolderWithCheats>

    @Insert
    fun insertGame(game: GameEntity): Long
}