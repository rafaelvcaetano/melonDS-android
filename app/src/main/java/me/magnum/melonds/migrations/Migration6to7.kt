package me.magnum.melonds.migrations

import android.content.SharedPreferences
import androidx.core.content.edit

class Migration6to7(private val sharedPreferences: SharedPreferences) : Migration {
    override val from: Int
        get() = 6
    override val to: Int
        get() = 7

    override fun migrate() {
        // Dir preferences now have a different format
        sharedPreferences.edit {
            putStringSet("bios_dir", emptySet())
            putStringSet("rom_search_dirs", emptySet())
            putStringSet("sram_dir", emptySet())
        }
    }
}