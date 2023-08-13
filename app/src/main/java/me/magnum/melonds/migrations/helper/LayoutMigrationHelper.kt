package me.magnum.melonds.migrations.helper

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.OutputStreamWriter

class LayoutMigrationHelper(
    private val context: Context,
    private val gson: Gson,
) {

    companion object {
        private const val LAYOUTS_DATA_FILE = "layouts.json"
    }

    fun <T, U> migrateLayouts(fromClass: Class<T>, layoutMapper: (T) -> U?) {
        val originalLayouts = getOriginalLayouts(fromClass)
        val newLayouts = originalLayouts.mapNotNull { layout ->
            layoutMapper(layout)
        }

        saveNewLayouts(newLayouts)
    }

    private fun <T> getOriginalLayouts(fromClass: Class<T>): List<T> {
        val cacheFile = File(context.filesDir, LAYOUTS_DATA_FILE)
        if (!cacheFile.isFile) {
            return emptyList()
        }

        val layoutListType = TypeToken.getParameterized(List::class.java, fromClass).type
        return runCatching {
            gson.fromJson<List<T>>(FileReader(cacheFile), layoutListType)
        }.onFailure {
            it.printStackTrace()
        }.getOrElse { emptyList() }
    }

    private fun <U> saveNewLayouts(roms: List<U>) {
        val cacheFile = File(context.filesDir, LAYOUTS_DATA_FILE)

        OutputStreamWriter(cacheFile.outputStream()).use {
            val romsJson = gson.toJson(roms)
            it.write(romsJson)
        }
    }
}