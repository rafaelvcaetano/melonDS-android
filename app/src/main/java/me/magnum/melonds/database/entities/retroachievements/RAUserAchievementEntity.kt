package me.magnum.melonds.database.entities.retroachievements

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "ra_user_achievement",
    primaryKeys = ["game_id", "achievement_id"],
)
data class RAUserAchievementEntity(
    @ColumnInfo(name = "game_id") val gameId: Long,
    @ColumnInfo(name = "achievement_id") val achievementId: Long,
    @ColumnInfo(name = "is_unlocked") val isUnlocked: Boolean,
)
