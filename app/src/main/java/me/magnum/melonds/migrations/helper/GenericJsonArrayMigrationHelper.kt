package me.magnum.melonds.migrations.helper

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.OutputStreamWriter

class GenericJsonArrayMigrationHelper(
    private val context: Context,
    private val gson: Gson,
) {

    inline fun <reified T, U> migrateJsonArrayData(jsonFile: String, noinline dataMapper: (T) -> U?) {
        migrateJsonArrayData(jsonFile, T::class.java, dataMapper)
    }

    fun <T, U> migrateJsonArrayData(jsonFile: String, fromClass: Class<T>, dataMapper: (T) -> U?) {
        val originalData = getOriginalData(jsonFile, fromClass)
        val newData = originalData.mapNotNull { data ->
            dataMapper(data)
        }

        saveNewData(jsonFile, newData)
    }

    private fun <T> getOriginalData(jsonFile: String, fromClass: Class<T>): List<T> {
        val dataFile = File(context.filesDir, jsonFile)
        if (!dataFile.isFile) {
            return emptyList()
        }

        val dataListType = TypeToken.getParameterized(List::class.java, fromClass).type
        return runCatching {
            gson.fromJson<List<T>>(FileReader(dataFile), dataListType)
        }.getOrElse { emptyList() }
    }

    private fun <U> saveNewData(jsonFile: String, data: List<U>) {
        val dataFile = File(context.filesDir, jsonFile)

        OutputStreamWriter(dataFile.outputStream()).use {
            val json = gson.toJson(data)
            it.write(json)
        }
    }
}