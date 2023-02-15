package me.magnum.melonds.database.entities.retroachievements

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "ra_pending_achievement_award",
    primaryKeys = ["achievement_id", "for_hardcore_mode"],
)
data class RAPendingAchievementSubmissionEntity(
    @ColumnInfo(name = "achievement_id") val achievementId: Long,
    @ColumnInfo(name = "game_id") val gameId: Long,
    @ColumnInfo(name = "for_hardcore_mode") val forHardcoreMode: Boolean,
)
