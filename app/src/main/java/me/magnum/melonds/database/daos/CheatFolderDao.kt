package me.magnum.melonds.database.daos

import androidx.room.Dao
import androidx.room.Insert
import me.magnum.melonds.database.entities.CheatFolderEntity

@Dao
interface CheatFolderDao {
    @Insert
    fun insertCheatFolder(cheatFolder: CheatFolderEntity): Long

    @Insert
    fun insertCheatFolders(cheatFolders: List<CheatFolderEntity>): List<Long>
}