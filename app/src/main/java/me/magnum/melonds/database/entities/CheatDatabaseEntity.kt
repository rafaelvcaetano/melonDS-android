package me.magnum.melonds.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cheat_database",
    indices = [
        Index(value = ["name"], unique = true)
    ]
)
data class CheatDatabaseEntity(
    @ColumnInfo(name = "id") @PrimaryKey(autoGenerate = true) val id: Long?,
    @ColumnInfo(name = "name") val name: String,
) {

    companion object {
        // The ID of the database that holds cheats manually created by the user. Its 0 because SQLite starts autoincrement keys from 1
        const val CUSTOM_CHEATS_DATABASE_ID = 0L
        const val CUSTOM_CHEATS_DATABASE_NAME = "__custom_cheat_database"
    }
}