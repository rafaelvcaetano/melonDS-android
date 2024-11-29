package me.magnum.melonds.impl

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.magnum.melonds.common.Deletable
import me.magnum.melonds.common.filterNotDeleted
import me.magnum.melonds.domain.model.Background
import me.magnum.melonds.domain.repositories.BackgroundRepository
import java.io.File
import java.io.FileReader
import java.io.OutputStreamWriter
import java.lang.reflect.Type
import java.util.UUID

class InternalBackgroundsRepository(private val context: Context, private val gson: Gson) : BackgroundRepository {
    companion object {
        private const val DATA_FILE = "backgrounds.json"
        private val backgroundListType: Type = object : TypeToken<List<Background>>(){}.type
    }

    private val backgroundLoadLock = Mutex()
    private var areBackgroundsLoaded = false
    private val backgrounds = MutableStateFlow<List<Deletable<Background>>>(emptyList())

    override fun getBackgrounds(): Flow<List<Background>> {
        return backgrounds
            .onStart { ensureBackgroundsAreLoaded() }
            .map { it.filterNotDeleted() }
    }

    override suspend fun getBackground(id: UUID): Background? {
        ensureBackgroundsAreLoaded()
        return backgrounds.value.firstOrNull { !it.isDeleted && it.data.id == id }?.data
    }

    override suspend fun addBackground(background: Background) {
        if (background.id == null) {
            val newBackground = background.copy(
                    id = UUID.randomUUID()
            )
            backgrounds.update {
                it.toMutableList().apply {
                    add(Deletable(newBackground, false))
                }
            }
        } else {
            val index = backgrounds.value.indexOfFirst { it.data.id == background.id }
            backgrounds.update {
                if (index >= 0) {
                    it.toMutableList().apply {
                        set(index, Deletable(background, false))
                    }
                } else {
                    it.toMutableList().apply {
                        add(Deletable(background, false))
                    }
                }
            }
        }
        saveBackgrounds()
    }

    override suspend fun deleteBackground(background: Background) {
        ensureBackgroundsAreLoaded()
        val backgroundToDelete = backgrounds.value.find { !it.isDeleted && it.data.id == background.id }
        if (backgroundToDelete != null) {
            backgrounds.update {
                val backgroundIndex = it.indexOf(backgroundToDelete)
                it.toMutableList().apply {
                    set(backgroundIndex, backgroundToDelete.copy(isDeleted = true))
                }
            }
            saveBackgrounds()
        }
    }

    private suspend fun ensureBackgroundsAreLoaded() = withContext(Dispatchers.IO) {
        backgroundLoadLock.withLock {
            if (areBackgroundsLoaded) {
                return@withContext
            } else {
                val deletableBackgrounds = loadBackgrounds().map { background -> Deletable(background, false) }
                backgrounds.value = deletableBackgrounds
                areBackgroundsLoaded = true
            }
        }
    }

    private fun loadBackgrounds(): List<Background> {
        val dataFile = File(context.filesDir, DATA_FILE)
        if (!dataFile.isFile) {
            return emptyList()
        }

        return try {
            val backgrounds = gson.fromJson<List<Background>>(FileReader(dataFile), backgroundListType)
            backgrounds ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun saveBackgrounds() = withContext(Dispatchers.IO) {
        val dataFile = File(context.filesDir, DATA_FILE)

        try {
            // Exclude deleted backgrounds
            val backgroundsToSave = backgrounds.value.filterNotDeleted()
            val layoutsJson = gson.toJson(backgroundsToSave)

            OutputStreamWriter(dataFile.outputStream()).use {
                it.write(layoutsJson)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}