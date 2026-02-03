package me.magnum.melonds.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration to RetroAchievements multi-sets. Alters `ra_achievement` and `ra_leaderboard` tables by changing the `game_id` columns to `set_id`. Add new `ra_achievement_set`
 * table.
 */
class Migration7to8 : Migration(7, 8) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `ra_achievement`")
        db.execSQL("DROP TABLE IF EXISTS `ra_leaderboard`")
        db.execSQL("DROP INDEX IF EXISTS `index_ra_achievement_game_id`")
        db.execSQL("DROP INDEX IF EXISTS `index_ra_leaderboard_game_id`")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `ra_achievement` (
                `id` INTEGER NOT NULL,
                `game_id` INTEGER NOT NULL,
                `set_id` INTEGER NOT NULL,
                `total_awards_casual` INTEGER NOT NULL,
                `total_awards_hardcore` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `points` INTEGER NOT NULL,
                `display_order` INTEGER NOT NULL,
                `badge_url_unlocked` TEXT NOT NULL,
                `badge_url_locked` TEXT NOT NULL,
                `memory_address` TEXT NOT NULL,
                `type` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `ra_leaderboard` (
                `id` INTEGER NOT NULL,
                `game_id` INTEGER NOT NULL,
                `set_id` INTEGER NOT NULL,
                `mem` TEXT NOT NULL,
                `format` TEXT NOT NULL,
                `lower_is_better` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `hidden` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `ra_achievement_set` (
                `id` INTEGER NOT NULL,
                `game_id` INTEGER NOT NULL,
                `title` TEXT,
                `type` TEXT NOT NULL,
                `icon_url` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ra_achievement_set_id` ON `ra_achievement` (`set_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ra_leaderboard_set_id` ON `ra_leaderboard` (`set_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ra_achievement_set_game_id` ON `ra_achievement_set` (`game_id`)")
    }
}