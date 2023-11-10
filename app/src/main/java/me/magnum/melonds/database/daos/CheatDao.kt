package me.magnum.melonds.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.reactivex.Single
import me.magnum.melonds.database.entities.CheatEntity
import me.magnum.melonds.database.entities.CheatStatusUpdate

@Dao
interface CheatDao {
    @Insert
    fun insertCheat(cheatEntity: CheatEntity): Long

    @Insert
    fun insertCheats(cheatEntities: List<CheatEntity>): List<Long>

    @Query("SELECT cheat.* FROM game LEFT JOIN cheat_folder ON game.id = cheat_folder.game_id LEFT JOIN cheat ON cheat_folder.id = cheat.cheat_folder_id WHERE game.game_code = :gameCode AND (game.game_checksum IS NULL OR game.game_checksum = :gameChecksum) AND cheat.enabled = 1")
    fun getEnabledRomCheats(gameCode: String, gameChecksum: String): Single<List<CheatEntity>>

    @Update(entity = CheatEntity::class)
    suspend fun updateCheatsStatus(cheats: List<CheatStatusUpdate>)
}