package me.magnum.melonds.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration5to6 : Migration(5, 6) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE ra_game")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS ra_game (
                game_id INTEGER PRIMARY KEY NOT NULL,
                rich_presence_patch TEXT,
                title TEXT NOT NULL,
                icon TEXT NOT NULL
            )
        """)
    }
}