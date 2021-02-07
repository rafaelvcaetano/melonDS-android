package me.magnum.melonds.database.entities

import androidx.room.Embedded
import androidx.room.Relation

class GameWithCheatCategories {
        @Embedded
        lateinit var game: GameEntity

        @Relation(
                entity = CheatFolderEntity::class,
                parentColumn = "id",
                entityColumn = "game_id"
        )
        lateinit var cheatFolders: List<CheatFolderWithCheats>
}