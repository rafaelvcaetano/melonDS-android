package me.magnum.melonds.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import io.reactivex.Maybe
import me.magnum.melonds.database.entities.GameEntity
import me.magnum.melonds.database.entities.GameWithCheatCategories

@Dao
interface GameDao {
    @Transaction
    @Query("SELECT * FROM game WHERE game_code = :gameCode AND (game_checksum IS NULL OR game_checksum = :gameChecksum)")
    fun findGameWithCheats(gameCode: String, gameChecksum: String): Maybe<List<GameWithCheatCategories>>

    @Insert
    fun insertGame(game: GameEntity): Long

    @Query("DELETE FROM game")
    fun deleteAll()
}