package me.magnum.melonds.database.entities

import androidx.room.Embedded
import androidx.room.Relation

class CheatFolderWithCheats {
        @Embedded
        lateinit var cheatFolder: CheatFolderEntity

        @Relation(
                parentColumn = "id",
                entityColumn = "cheat_folder_id"
        )
        lateinit var cheats: List<CheatEntity>
}