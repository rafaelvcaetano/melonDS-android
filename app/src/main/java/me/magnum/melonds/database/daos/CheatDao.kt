package me.magnum.melonds.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.reactivex.Single
import me.magnum.melonds.database.entities.CheatEntity
import me.magnum.melonds.database.entities.CheatStatusUpdate
import me.magnum.melonds.domain.model.Cheat

@Dao
interface CheatDao {
    @Insert
    fun insertCheat(cheatEntity: CheatEntity): Long

    @Insert
    fun insertCheats(cheatEntities: List<CheatEntity>): List<Long>

    @Query("SELECT * FROM cheat LEFT JOIN cheat_folder ON cheat_folder.id = cheat.cheat_folder_id LEFT JOIN game ON game.id = cheat_folder.game_id WHERE game.game_code = :gameCode AND cheat.enabled = 1")
    fun getEnabledRomCheats(gameCode: String): Single<List<Cheat>>

    @Update(entity = CheatEntity::class)
    fun updateCheatsStatus(cheats: List<CheatStatusUpdate>)
}