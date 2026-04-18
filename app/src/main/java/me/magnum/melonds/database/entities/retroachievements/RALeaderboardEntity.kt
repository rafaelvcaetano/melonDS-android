package me.magnum.melonds.database.entities.retroachievements

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ra_leaderboard",
    indices = [
        Index("set_id")
    ],
)
data class RALeaderboardEntity(
    @PrimaryKey @ColumnInfo("id") val id: Long,
    @ColumnInfo(name = "game_id") val gameId: Long,
    @ColumnInfo(name = "set_id") val setId: Long,
    @ColumnInfo("mem") val mem: String,
    @ColumnInfo("format") val format: String,
    @ColumnInfo("lower_is_better") val lowerIsBetter: Boolean,
    @ColumnInfo("title") val title: String,
    @ColumnInfo("description") val description: String,
    @ColumnInfo("hidden") val hidden: Boolean,
)