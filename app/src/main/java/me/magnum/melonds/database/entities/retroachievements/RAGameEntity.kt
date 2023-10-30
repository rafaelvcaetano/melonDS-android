package me.magnum.melonds.database.entities.retroachievements

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ra_game")
data class RAGameEntity(
    @PrimaryKey @ColumnInfo(name = "game_id") val gameId: Long,
    @ColumnInfo(name = "rich_presence_patch") val richPresencePatch: String?,
    @ColumnInfo(name = "icon") val icon: String,
)