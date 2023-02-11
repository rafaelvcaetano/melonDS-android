package me.magnum.melonds.database.entities.retroachievements

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "ra_user_achievement",
    primaryKeys = ["game_id", "achievement_id"],
    foreignKeys = [
        ForeignKey(
            entity = RAAchievementEntity::class,
            parentColumns = ["id"],
            childColumns = ["achievement_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class RAUserAchievementEntity(
    @ColumnInfo(name = "game_id") val gameId: Long,
    @ColumnInfo(name = "achievement_id") val achievementId: Long,
    @ColumnInfo(name = "is_unlocked") val isUnlocked: Boolean,
)
