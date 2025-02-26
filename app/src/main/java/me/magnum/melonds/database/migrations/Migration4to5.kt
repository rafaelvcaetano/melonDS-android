package me.magnum.melonds.database.migrations

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.magnum.melonds.database.entities.CheatDatabaseEntity

class Migration4to5 : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // game_checksum was made NOT NULL. Ensure all bad entries are removed
        db.execSQL("DELETE FROM game WHERE game_checksum IS NULL")

        // Update cheat table to add cheat_database_id column
        db.execSQL("CREATE TABLE IF NOT EXISTS `_new_cheat` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `cheat_folder_id` INTEGER NOT NULL, `cheat_database_id` INTEGER NOT NULL, `name` TEXT NOT NULL, `description` TEXT, `code` TEXT NOT NULL, `enabled` INTEGER NOT NULL, FOREIGN KEY(`cheat_folder_id`) REFERENCES `cheat_folder`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`cheat_database_id`) REFERENCES `cheat_database`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("INSERT INTO `_new_cheat` (`id`,`cheat_folder_id`,`cheat_database_id`,`name`,`description`,`code`,`enabled`) SELECT `cheat`.`id`,`cheat`.`cheat_folder_id`,`game`.`database_id`,`cheat`.`name`,`cheat`.`description`,`cheat`.`code`,`cheat`.`enabled` FROM `cheat` LEFT JOIN `cheat_folder` ON `cheat`.`cheat_folder_id` = `cheat_folder`.`id` LEFT JOIN `game` ON `cheat_folder`.`game_id` = `game`.`id`")
        db.execSQL("DROP TABLE `cheat`")
        db.execSQL("ALTER TABLE `_new_cheat` RENAME TO `cheat`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cheat_cheat_folder_id` ON `cheat` (`cheat_folder_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cheat_cheat_database_id` ON `cheat` (`cheat_database_id`)")

        // Update game table to remove database_id column and make game_checksum not null
        db.execSQL("CREATE TABLE IF NOT EXISTS `_new_game` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `name` TEXT NOT NULL, `game_code` TEXT NOT NULL, `game_checksum` TEXT NOT NULL)")
        db.execSQL("INSERT INTO `_new_game` (`id`,`name`,`game_code`,`game_checksum`) SELECT `id`,`name`,`game_code`,`game_checksum` FROM `game`")
        db.execSQL("DROP TABLE `game`")
        db.execSQL("ALTER TABLE `_new_game` RENAME TO `game`")
        // Delete duplicate entries in game before creating new UNIQUE INDEX
        db.execSQL("DELETE FROM game WHERE id IN (SELECT id FROM (SELECT id, game_code, game_checksum, ROW_NUMBER() OVER (PARTITION BY game_code, game_checksum ORDER BY id) AS row_num FROM game) WHERE row_num > 1)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `game_code_checksum_index` ON `game` (`game_code`, `game_checksum`)")

        // Create custom cheat database
        val contentValues = ContentValues().apply {
            put("id", CheatDatabaseEntity.CUSTOM_CHEATS_DATABASE_ID)
            put("name", CheatDatabaseEntity.CUSTOM_CHEATS_DATABASE_NAME)
        }
        db.insert("cheat_database", SQLiteDatabase.CONFLICT_REPLACE, contentValues)
    }
}