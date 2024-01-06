package me.magnum.melonds.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration1to2 : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE game ADD COLUMN game_checksum TEXT")
        db.execSQL("CREATE INDEX index_game_game_checksum ON game(game_checksum)")
    }
}