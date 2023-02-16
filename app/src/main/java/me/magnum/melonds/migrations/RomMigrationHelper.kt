package me.magnum.melonds.migrations

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.OutputStreamWriter
import java.lang.reflect.Type

class RomMigrationHelper(
    private val context: Context,
    private val gson: Gson,
) {

    companion object {
        private const val ROM_DATA_FILE = "rom_data.json"
    }

    fun <T, U> migrateRoms(romMapper: (T) -> U?) {
        val originalRoms = getOriginalRoms<T>()
        val newRoms = originalRoms.mapNotNull { rom ->
            romMapper(rom)
        }

        saveNewRoms(newRoms)
    }

    private fun <T> getOriginalRoms(): List<T> {
        val cacheFile = File(context.filesDir, ROM_DATA_FILE)
        if (!cacheFile.isFile) {
            return emptyList()
        }

        val romListType: Type = object : TypeToken<List<T>>(){}.type
        return runCatching {
            gson.fromJson<List<T>>(FileReader(cacheFile), romListType)
        }.getOrElse { emptyList() }
    }

    private fun <U> saveNewRoms(roms: List<U>) {
        val cacheFile = File(context.filesDir, ROM_DATA_FILE)

        OutputStreamWriter(cacheFile.outputStream()).use {
            val romsJson = gson.toJson(roms)
            it.write(romsJson)
        }
    }
}