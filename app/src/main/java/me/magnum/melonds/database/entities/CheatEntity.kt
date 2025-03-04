package me.magnum.melonds.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "cheat",
    foreignKeys = [
        ForeignKey(
            entity = CheatFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["cheat_folder_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CheatDatabaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["cheat_database_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CheatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long?,
    @ColumnInfo(name = "cheat_folder_id", index = true) val cheatFolderId: Long,
    @ColumnInfo(name = "cheat_database_id", index = true) val cheatDatabaseId: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "code") val code: String,
    @ColumnInfo(name = "enabled") val enabled: Boolean
)