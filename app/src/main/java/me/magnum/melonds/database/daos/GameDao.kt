package me.magnum.melonds.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import me.magnum.melonds.database.entities.GameEntity
import me.magnum.melonds.database.entities.GameWithCheatCategories

@Dao
interface GameDao {
    @Transaction
    @Query("SELECT * FROM game WHERE game_code = :gameCode")
    fun findGameWithCheats(gameCode: String): Maybe<List<GameWithCheatCategories>>

    @Insert
    fun insertGame(game: GameEntity): Long
}