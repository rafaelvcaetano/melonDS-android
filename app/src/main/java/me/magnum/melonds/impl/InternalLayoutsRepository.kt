package me.magnum.melonds.impl

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.reactivex.Single
import me.magnum.melonds.domain.model.LayoutConfiguration
import me.magnum.melonds.domain.repositories.LayoutsRepository
import java.io.File
import java.io.FileReader
import java.lang.reflect.Type
import java.util.*

class InternalLayoutsRepository(private val context: Context, private val gson: Gson) : LayoutsRepository {
    companion object {
        private const val DATA_FILE = "layouts.json"
        private val layoutListType: Type = object : TypeToken<List<LayoutConfiguration>>(){}.type
    }

    private val layouts = mutableListOf<LayoutConfiguration>()

    override fun getLayouts(): Single<List<LayoutConfiguration>> {
        return Single.create { emitter ->
            val dataFile = File(context.filesDir, DATA_FILE)
            if (!dataFile.isFile) {
                emitter.onSuccess(emptyList())
                return@create
            }

            try {
                val roms = gson.fromJson<List<LayoutConfiguration>>(FileReader(dataFile), layoutListType)
                emitter.onSuccess(roms ?: emptyList())
            } catch (_: Exception) {
                emitter.onSuccess(emptyList())
            }
        }
    }

    override fun saveLayout(layout: LayoutConfiguration) {
        if (layout.id == null) {
            val newLayout = layout.copy(
                    id = UUID.randomUUID()
            )
            layouts.add(newLayout)
        } else {
            val index = layouts.indexOfFirst { it.id == layout.id }
            if (index >= 0) {
                layouts[index] = layout
            } else {
                layouts.add(layout)
            }
        }
    }
}