package me.magnum.melonds.impl

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.magnum.melonds.R
import me.magnum.melonds.common.Deletable
import me.magnum.melonds.common.filterNotDeleted
import me.magnum.melonds.domain.model.layout.LayoutConfiguration
import me.magnum.melonds.domain.repositories.LayoutsRepository
import me.magnum.melonds.impl.dtos.layout.LayoutConfigurationDto
import java.io.File
import java.io.FileReader
import java.io.OutputStreamWriter
import java.lang.reflect.Type
import java.util.UUID

class InternalLayoutsRepository(private val context: Context, private val gson: Gson) : LayoutsRepository {
    companion object {
        private const val DATA_FILE = "layouts.json"
        private val layoutListType: Type = object : TypeToken<List<LayoutConfigurationDto>>(){}.type
    }

    private val layoutsLoadLock = Mutex()
    private var areLayoutsLoaded = false
    private val layouts = MutableStateFlow<List<Deletable<LayoutConfiguration>>>(emptyList())

    private val mGlobalLayoutPlaceholder by lazy {
        LayoutConfiguration(
            null,
            context.getString(R.string.use_global_layout),
            LayoutConfiguration.LayoutType.DEFAULT,
            LayoutConfiguration.LayoutOrientation.FOLLOW_SYSTEM,
            false,
            0,
            emptyMap(),
        )
    }

    override fun getLayouts(): Flow<List<LayoutConfiguration>> {
        return layouts
            .onStart { ensureLayoutsAreLoaded() }
            .map { it.filterNotDeleted() }
    }

    override suspend fun getLayout(id: UUID): LayoutConfiguration? {
        ensureLayoutsAreLoaded()
        return layouts.value.firstOrNull { !it.isDeleted && it.data.id == id }?.data
    }

    override suspend fun deleteLayout(layout: LayoutConfiguration) {
        ensureLayoutsAreLoaded()
        val layoutToDelete = layouts.value.find { !it.isDeleted && it.data.id == layout.id }
        if (layoutToDelete != null) {
            layouts.update {
                val layoutIndex = it.indexOf(layoutToDelete)
                it.toMutableList().apply {
                    set(layoutIndex, layoutToDelete.copy(isDeleted = true))
                }
            }
            saveLayouts()
        }
    }

    override fun getGlobalLayoutPlaceholder(): LayoutConfiguration {
        return mGlobalLayoutPlaceholder
    }

    override fun observeLayout(id: UUID): Flow<LayoutConfiguration> {
        return layouts
            .onStart { ensureLayoutsAreLoaded() }
            .map { layouts -> layouts.firstOrNull { !it.isDeleted && it.data.id == id }?.data }
            .takeWhile { it != null }
            .filterNotNull()
    }

    override suspend fun saveLayout(layout: LayoutConfiguration) {
        ensureLayoutsAreLoaded()
        if (layout.id == null) {
            val newLayout = layout.copy(
                id = UUID.randomUUID()
            )
            layouts.update {
                it.toMutableList().apply {
                    add(Deletable(newLayout, false))
                }
            }
        } else {
            val index = layouts.value.indexOfFirst { it.data.id == layout.id }
            layouts.update {
                it.toMutableList().apply {
                    if (index >= 0) {
                        // Replace existing
                        set(index, Deletable(layout, false))
                    } else {
                        // Add new one
                        add(Deletable(layout, false))
                    }
                }
            }
        }
        saveLayouts()
    }

    private suspend fun ensureLayoutsAreLoaded() = withContext(Dispatchers.IO) {
        layoutsLoadLock.withLock {
            if (areLayoutsLoaded) {
                return@withLock
            } else {
                val deletableLayouts = loadLayouts().map { Deletable(it, false) }
                layouts.value = buildList {
                    add(Deletable(buildDefaultLayout(), false))
                    addAll(deletableLayouts)
                }
                areLayoutsLoaded = true
            }
        }
    }

    private fun loadLayouts(): List<LayoutConfiguration> {
        val dataFile = File(context.filesDir, DATA_FILE)
        if (!dataFile.isFile) {
            return emptyList()
        }

        return try {
            val layouts = gson.fromJson<List<LayoutConfigurationDto>>(FileReader(dataFile), layoutListType)?.map {
                it.toModel()
            }
            layouts ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun saveLayouts() = withContext(Dispatchers.IO) {
        val dataFile = File(context.filesDir, DATA_FILE)

        try {
            val customLayoutsDtos = layouts.value.mapNotNull {
                // Exclude deleted and default layouts. They shouldn't be saved
                if (it.isDeleted || it.data.type == LayoutConfiguration.LayoutType.DEFAULT) {
                    null
                } else {
                    LayoutConfigurationDto.fromModel(it.data)
                }
            }
            val layoutsJson = gson.toJson(customLayoutsDtos)

            OutputStreamWriter(dataFile.outputStream()).use {
                it.write(layoutsJson)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildDefaultLayout(): LayoutConfiguration {
        return LayoutConfiguration(
            id = LayoutConfiguration.DEFAULT_ID,
            name = context.getString(R.string.default_layout_name),
            type = LayoutConfiguration.LayoutType.DEFAULT,
            orientation = LayoutConfiguration.LayoutOrientation.FOLLOW_SYSTEM,
            useCustomOpacity = false,
            opacity = 50,
            layoutVariants = emptyMap(),
        )
    }
}