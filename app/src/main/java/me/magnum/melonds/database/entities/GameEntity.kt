package me.magnum.melonds.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "game",
    foreignKeys = [
        ForeignKey(
            entity = CheatDatabaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["database_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ]
)
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long?,
    @ColumnInfo(name = "database_id") val databaseId: Long?,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "game_code", index = true) val gameCode: String,
    @ColumnInfo(name = "game_checksum", index = true) val gameChecksum: String?
)