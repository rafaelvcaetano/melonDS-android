package me.magnum.melonds.database.entities.retroachievements

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ra_achievement",
    indices = [
        Index("set_id")
    ]
)
data class RAAchievementEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "game_id") val gameId: Long,
    @ColumnInfo(name = "set_id") val setId: Long,
    @ColumnInfo(name = "total_awards_casual") val totalAwardsCasual: Int,
    @ColumnInfo(name = "total_awards_hardcore") val totalAwardsHardcore: Int,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "points") val points: Int,
    @ColumnInfo(name = "display_order") val displayOrder: Int,
    @ColumnInfo(name = "badge_url_unlocked") val badgeUrlUnlocked: String,
    @ColumnInfo(name = "badge_url_locked") val badgeUrlLocked: String,
    @ColumnInfo(name = "memory_address") val memoryAddress: String,
    @ColumnInfo(name = "type") val type: Int,
)