package me.magnum.melonds.database

import androidx.room.Database
import androidx.room.RoomDatabase
import me.magnum.melonds.database.daos.CheatFolderDao
import me.magnum.melonds.database.daos.CheatDao
import me.magnum.melonds.database.daos.GameDao
import me.magnum.melonds.database.entities.CheatEntity
import me.magnum.melonds.database.entities.CheatFolderEntity
import me.magnum.melonds.database.entities.GameEntity

@Database(entities = [GameEntity::class, CheatFolderEntity::class, CheatEntity::class], version = 1)
abstract class MelonDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun cheatFolderDao(): CheatFolderDao
    abstract fun cheatDao(): CheatDao
}