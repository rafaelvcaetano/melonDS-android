package me.magnum.melonds.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import me.magnum.melonds.database.entities.CheatFolderEntity

@Dao
interface CheatFolderDao {
    @Insert
    suspend fun insertCheatFolder(cheatFolder: CheatFolderEntity): Long

    @Insert
    suspend fun insertCheatFolders(cheatFolders: List<CheatFolderEntity>): List<Long>

    @Query("DELETE FROM cheat_folder WHERE id NOT IN (SELECT DISTINCT cheat_folder_id FROM cheat)")
    suspend fun deleteEmptyFolders()
}