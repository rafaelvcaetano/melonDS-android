package me.magnum.melonds.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import me.magnum.melonds.database.entities.CheatDatabaseEntity

@Dao
interface CheatDatabaseDao {
    @Insert
    suspend fun insertCheatDatabase(database: CheatDatabaseEntity): Long

    @Query("DELETE FROM cheat_database WHERE id <> ${CheatDatabaseEntity.CUSTOM_CHEATS_DATABASE_ID}")
    suspend fun deleteImportedCheatDatabases()
}