package me.magnum.melonds.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import me.magnum.melonds.database.daos.CheatFolderDao
import me.magnum.melonds.database.daos.CheatDao
import me.magnum.melonds.database.daos.CheatDatabaseDao
import me.magnum.melonds.database.daos.GameDao
import me.magnum.melonds.database.entities.CheatDatabaseEntity
import me.magnum.melonds.database.entities.CheatEntity
import me.magnum.melonds.database.entities.CheatFolderEntity
import me.magnum.melonds.database.entities.GameEntity

@Database(
    version = 3,
    exportSchema = true,
    entities = [CheatDatabaseEntity::class, GameEntity::class, CheatFolderEntity::class, CheatEntity::class],
    autoMigrations = [
        AutoMigration(
            from = 2,
            to = 3,
            spec = MelonDatabase.Migration2to3Spec::class,
        )
    ]
)
abstract class MelonDatabase : RoomDatabase() {
    abstract fun cheatDatabaseDao(): CheatDatabaseDao
    abstract fun gameDao(): GameDao
    abstract fun cheatFolderDao(): CheatFolderDao
    abstract fun cheatDao(): CheatDao

    class Migration2to3Spec : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            val result = db.query("SELECT COUNT(*) FROM game")
            if (result.moveToFirst() && result.getInt(0) > 0) {
                // If there are games, insert a cheat database. Most likely the cheat database that was used was the one
                // from DeadSkullzJr, which has the name "DeadSkullzJr's NDS Cheat Database"
                val contentValues = ContentValues().apply {
                    put("name", "DeadSkullzJr's NDS Cheat Database")
                }
                val databaseId = db.insert("cheat_database", SQLiteDatabase.CONFLICT_IGNORE, contentValues)
                db.execSQL("UPDATE game SET database_id = ?", arrayOf(databaseId))
            }
        }
    }
}