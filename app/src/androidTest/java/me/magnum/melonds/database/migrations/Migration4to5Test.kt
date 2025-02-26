package me.magnum.melonds.database.migrations

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.magnum.melonds.database.MelonDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-test-db"

@RunWith(AndroidJUnit4::class)
class Migration4to5Test {

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MelonDatabase::class.java,
    )

    @Test
    fun games_with_null_game_checksum_are_removed() {
        migrationTestHelper.createDatabase(TEST_DB, 4).use { db ->
            val cheatDatabase = ContentValues().apply {
                put("id", 0L)
                put("name", "database")
            }
            val validGame = ContentValues().apply {
                put("id", 0L)
                put("database_id", 0L)
                put("name", "Game0")
                put("game_code", "code")
                put("game_checksum", "123")
            }
            val invalidGame = ContentValues().apply {
                put("id", 1L)
                put("database_id", 0L)
                put("name", "Game1")
                put("game_code", "code")
                putNull("game_checksum")
            }

            db.insert("cheat_database", SQLiteDatabase.CONFLICT_IGNORE, cheatDatabase)
            db.insert("game", SQLiteDatabase.CONFLICT_FAIL, validGame)
            db.insert("game", SQLiteDatabase.CONFLICT_FAIL, invalidGame)
        }

        migrationTestHelper.runMigrationsAndValidate(TEST_DB, 5, true, Migration4to5()).use { db ->
            db.query("SELECT * FROM game").use {
                assertEquals(1, it.count)
            }
        }
    }

    @Test
    fun cheats_have_their_cheat_database_id_set_with_the_value_extracted_from_their_game() {
        migrationTestHelper.createDatabase(TEST_DB, 4).use { db ->
            val cheatDatabase = ContentValues().apply {
                put("id", 5L)
                put("name", "database")
            }
            val game = ContentValues().apply {
                put("id", 0L)
                put("database_id", 5L)
                put("name", "Game0")
                put("game_code", "code")
                put("game_checksum", "123")
            }
            val folder = ContentValues().apply {
                put("id", 0L)
                put("game_id", 0L)
                put("name", "Folder")
            }
            val cheat = ContentValues().apply {
                put("id", 0L)
                put("cheat_folder_id", 0L)
                put("name", "cheat")
                put("description", "description")
                put("code", "code")
                put("enabled", 0)
            }

            db.insert("cheat_database", SQLiteDatabase.CONFLICT_IGNORE, cheatDatabase)
            db.insert("game", SQLiteDatabase.CONFLICT_IGNORE, game)
            db.insert("cheat_folder", SQLiteDatabase.CONFLICT_IGNORE, folder)
            db.insert("cheat", SQLiteDatabase.CONFLICT_IGNORE, cheat)
        }

        migrationTestHelper.runMigrationsAndValidate(TEST_DB, 5, true, Migration4to5()).use { db ->
            db.query("SELECT * FROM cheat").use {
                val cheatDatabaseIdIndex = it.getColumnIndex("cheat_database_id")
                while (it.moveToNext()) {
                    val cheatDatabaseId = it.getLong(cheatDatabaseIdIndex)
                    assertEquals(5L, cheatDatabaseId)
                }
            }
        }
    }

    @Test
    fun games_with_duplicate_codes_and_checksums_are_removed() {
        migrationTestHelper.createDatabase(TEST_DB, 4).use { db ->
            val cheatDatabase = ContentValues().apply {
                put("id", 0L)
                put("name", "database")
            }
            val uniqueGame1 = ContentValues().apply {
                put("id", 0L)
                put("database_id", 0L)
                put("name", "Game0")
                put("game_code", "001")
                put("game_checksum", "001")
            }
            val duplicatedGame1 = ContentValues().apply {
                put("id", 1L)
                put("database_id", 0L)
                put("name", "Game1")
                put("game_code", "002")
                put("game_checksum", "002")
            }
            val duplicatedGame2 = ContentValues().apply {
                put("id", 2L)
                put("database_id", 0L)
                put("name", "Game2")
                put("game_code", "002")
                put("game_checksum", "002")
            }

            db.insert("cheat_database", SQLiteDatabase.CONFLICT_IGNORE, cheatDatabase)
            db.insert("game", SQLiteDatabase.CONFLICT_IGNORE, uniqueGame1)
            db.insert("game", SQLiteDatabase.CONFLICT_IGNORE, duplicatedGame1)
            db.insert("game", SQLiteDatabase.CONFLICT_IGNORE, duplicatedGame2)
        }

        migrationTestHelper.runMigrationsAndValidate(TEST_DB, 5, true, Migration4to5()).use { db ->
            db.query("SELECT * FROM game").use {
                val gameNameIndex = it.getColumnIndex("name")
                assertTrue(it.moveToNext())
                assertEquals("Game0", it.getString(gameNameIndex))

                assertTrue(it.moveToNext())
                assertEquals("Game1", it.getString(gameNameIndex))

                assertFalse(it.moveToNext())
            }
        }
    }
}