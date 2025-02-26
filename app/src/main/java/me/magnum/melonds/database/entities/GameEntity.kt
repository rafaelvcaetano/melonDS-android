package me.magnum.melonds.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "game",
    indices = [
        Index(
            value = ["game_code", "game_checksum"],
            name = "game_code_checksum_index",
            unique = true,
        ),
    ]
)
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long?,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "game_code") val gameCode: String,
    @ColumnInfo(name = "game_checksum") val gameChecksum: String,
)