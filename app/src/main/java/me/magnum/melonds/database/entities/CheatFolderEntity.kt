package me.magnum.melonds.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
        tableName = "cheat_folder",
        foreignKeys = [
            ForeignKey(
                    entity = GameEntity::class,
                    parentColumns = ["id"],
                    childColumns = ["game_id"],
                    onDelete = ForeignKey.CASCADE
            )
        ]
)
data class CheatFolderEntity(
        @PrimaryKey(autoGenerate = true) val id: Long?,
        @ColumnInfo(name = "game_id", index = true) val gameId: Long,
        @ColumnInfo(name = "name") val name: String
)