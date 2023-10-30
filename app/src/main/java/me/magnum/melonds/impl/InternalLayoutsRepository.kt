package me.magnum.melonds.impl

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import me.magnum.melonds.R
import me.magnum.melonds.common.Deletable
import me.magnum.melonds.domain.model.LayoutConfiguration
import me.magnum.melonds.domain.model.UILayout
import me.magnum.melonds.domain.repositories.LayoutsRepository
import me.magnum.melonds.impl.dtos.layout.LayoutConfigurationDto
import java.io.File
import java.io.FileReader
import java.io.OutputStreamWriter
import java.lang.reflect.Type
import java.util.*

class InternalLayoutsRepository(private val context: Context, private val gson: Gson, private val defaultLayoutProvider: DefaultLayoutProvider) : LayoutsRepository {
    companion object {
        private const val DATA_FILE = "layouts.json"
        private val layoutListType: Type = object : TypeToken<List<LayoutConfigurationDto>>(){}.type
    }

    private var areLayoutsLoaded = false
    private val layouts = mutableListOf<Deletable<LayoutConfiguration>>()

    private val layoutsChangedSubject = PublishSubject.create<Unit>()
    private val mGlobalLayoutPlaceholder by lazy {
        LayoutConfiguration(
                null,
                context.getString(R.string.use_global_layout),
                LayoutConfiguration.LayoutType.DEFAULT,
                LayoutConfiguration.LayoutOrientation.FOLLOW_SYSTEM,
                false,
                0,
                UILayout(emptyList()),
                UILayout(emptyList())
        )
    }

    override fun getLayouts(): Observable<List<LayoutConfiguration>> {
        return getCachedLayoutsOrLoad()
                .toObservable()
                .concatWith(layoutsChangedSubject.map { layouts })
                .map {
                    it.mapNotNull { deletableLayout ->
                        if (deletableLayout.isDeleted) {
                            null
                        } else {
                            deletableLayout.data
                        }
                    }
                }
    }

    override fun getLayout(id: UUID): Maybe<LayoutConfiguration> {
        return getCachedLayoutsOrLoad().flatMapMaybe { layouts ->
            val layoutDeletable = layouts.firstOrNull { !it.isDeleted && it.data.id == id }
            if (layoutDeletable != null) {
                Maybe.just(layoutDeletable.data)
            } else {
                Maybe.empty()
            }
        }
    }

    override fun deleteLayout(layout: LayoutConfiguration): Completable {
        return getCachedLayoutsOrLoad().doAfterSuccess {
            layouts.find { !it.isDeleted && it.data.id == layout.id }?.let {
                // Perform soft delete
                it.isDeleted = true
                layoutsChangedSubject.onNext(Unit)
                saveLayouts()
            }
        }.ignoreElement()
    }

    override fun getGlobalLayoutPlaceholder(): LayoutConfiguration {
        return mGlobalLayoutPlaceholder
    }

    override fun observeLayout(id: UUID): Observable<LayoutConfiguration> {
        return getCachedLayoutsOrLoad()
                .toObservable()
                .concatWith(layoutsChangedSubject.map { layouts })
                .takeWhile { layouts ->
                    // Take events while the observed layout is present
                    layouts.any { !it.isDeleted && it.data.id == id }
                }
                .map { layouts ->
                    layouts.first { !it.isDeleted && it.data.id == id }.data
                }
    }

    override fun saveLayout(layout: LayoutConfiguration) {
        if (layout.id == null) {
            val newLayout = layout.copy(
                    id = UUID.randomUUID()
            )
            layouts.add(Deletable(newLayout, false))
        } else {
            val index = layouts.indexOfFirst { it.data.id == layout.id }
            if (index >= 0) {
                layouts[index] = Deletable(layout, false)
            } else {
                layouts.add(Deletable(layout, false))
            }
        }
        layoutsChangedSubject.onNext(Unit)
        saveLayouts()
    }

    private fun getCachedLayoutsOrLoad(): Single<List<Deletable<LayoutConfiguration>>> {
        return if (areLayoutsLoaded) {
            Single.just(layouts)
        } else {
            loadLayouts().map {
                layouts.clear()
                layouts.add(Deletable(buildDefaultLayout(), false))
                layouts.addAll(it.map { layout -> Deletable(layout, false) })
                areLayoutsLoaded = true
                layouts
            }
        }
    }

    private fun loadLayouts(): Single<List<LayoutConfiguration>> {
        return Single.create { emitter ->
            val dataFile = File(context.filesDir, DATA_FILE)
            if (!dataFile.isFile) {
                emitter.onSuccess(emptyList())
                return@create
            }

            try {
                val layouts = gson.fromJson<List<LayoutConfigurationDto>>(FileReader(dataFile), layoutListType)?.map {
                    it.toModel()
                }
                emitter.onSuccess(layouts ?: emptyList())
            } catch (_: Exception) {
                emitter.onSuccess(emptyList())
            }
        }
    }

    private fun saveLayouts() {
        val dataFile = File(context.filesDir, DATA_FILE)

        try {
            val customLayoutsDtos = layouts.mapNotNull {
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
        return defaultLayoutProvider.defaultLayout.copy(
                id = LayoutConfiguration.DEFAULT_ID,
                name = context.getString(R.string.default_layout_name),
                type = LayoutConfiguration.LayoutType.DEFAULT
        )
    }
}