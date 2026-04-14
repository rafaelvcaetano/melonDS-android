package me.magnum.melonds.database.entities.retroachievements

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ra_achievement_set",
    indices = [
        Index("game_id")
    ],
)
data class RAAchievementSetEntity(
    @ColumnInfo("id") @PrimaryKey val id: Long,
    @ColumnInfo("game_id") val gameId: Long,
    @ColumnInfo("title") val title: String?,
    @ColumnInfo("type") val type: String,
    @ColumnInfo("icon_url") val iconUrl: String,
)