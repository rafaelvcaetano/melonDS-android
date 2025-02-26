package me.magnum.melonds.database.callback

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import me.magnum.melonds.database.entities.CheatDatabaseEntity

class CustomCheatCreationCallback : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        val contentValues = ContentValues().apply {
            put("id", CheatDatabaseEntity.CUSTOM_CHEATS_DATABASE_ID)
            put("name", CheatDatabaseEntity.CUSTOM_CHEATS_DATABASE_NAME)
        }

        db.insert("cheat_database", SQLiteDatabase.CONFLICT_REPLACE, contentValues)
    }
}