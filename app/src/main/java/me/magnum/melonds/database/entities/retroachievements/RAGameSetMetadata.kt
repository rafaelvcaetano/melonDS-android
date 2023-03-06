package me.magnum.melonds.database.entities.retroachievements

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "ra_game_set_metadata")
data class RAGameSetMetadata(
    @PrimaryKey @ColumnInfo(name = "game_id") val gameId: Long,
    @ColumnInfo(name = "last_achievement_set_updated") val lastAchievementSetUpdated: Instant?,
    @ColumnInfo(name = "last_user_data_updated") val lastUserDataUpdated: Instant?,
)
