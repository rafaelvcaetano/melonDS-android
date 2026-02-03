package me.magnum.melonds.database.entities.retroachievements

import androidx.room.Embedded
import androidx.room.Relation

data class RAGameWithSetsEntity(
    @Embedded val game: RAGameEntity,
    @Relation(
        entity = RAAchievementSetEntity::class,
        parentColumn = "game_id",
        entityColumn = "game_id",
    )
    val sets: List<RAAchievementSetWithDataEntity>,
)