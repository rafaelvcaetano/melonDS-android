package me.magnum.melonds.database.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.magnum.melonds.database.entities.CheatEntity
import me.magnum.melonds.database.entities.CheatStatusUpdate

@Dao
interface CheatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheat(cheatEntity: CheatEntity): Long

    @Insert
    suspend fun insertCheats(cheatEntities: List<CheatEntity>): List<Long>

    @Query("SELECT * FROM cheat WHERE id = :cheatId")
    suspend fun getCheat(cheatId: Long): CheatEntity?

    @Query("DELETE FROM cheat WHERE id = :cheatId")
    suspend fun deleteCheat(cheatId: Long)

    @Query("SELECT cheat.* FROM game LEFT JOIN cheat_folder ON game.id = cheat_folder.game_id LEFT JOIN cheat ON cheat_folder.id = cheat.cheat_folder_id WHERE game.game_code = :gameCode AND (game.game_checksum IS NULL OR game.game_checksum = :gameChecksum) AND cheat.enabled = 1")
    suspend fun getEnabledRomCheats(gameCode: String, gameChecksum: String): List<CheatEntity>

    @Query("SELECT * FROM cheat WHERE cheat_folder_id = :folderId")
    fun getFolderCheats(folderId: Long): Flow<List<CheatEntity>>

    @Update(entity = CheatEntity::class)
    suspend fun updateCheatsStatus(cheats: List<CheatStatusUpdate>)
}