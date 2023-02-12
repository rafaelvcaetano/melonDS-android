package me.magnum.melonds.database.entities.retroachievements

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ra_game_hash_library")
data class RAGameHashEntity(
    @PrimaryKey @ColumnInfo(name = "game_hash") val gameHash: String,
    @ColumnInfo(name = "game_id") val gameId: Long,
)
