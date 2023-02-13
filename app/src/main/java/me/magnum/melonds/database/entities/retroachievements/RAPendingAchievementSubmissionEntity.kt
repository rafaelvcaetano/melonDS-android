package me.magnum.melonds.database.entities.retroachievements

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ra_pending_achievement_award")
data class RAPendingAchievementSubmissionEntity(
    @PrimaryKey @ColumnInfo(name = "achievement_id") val achievementId: Long,
    @ColumnInfo(name = "for_hardcore_mode") val forHardcoreMode: Boolean,
)
