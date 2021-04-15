package me.magnum.melonds.impl

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import me.magnum.melonds.domain.model.Background
import me.magnum.melonds.domain.repositories.BackgroundRepository
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.io.OutputStreamWriter
import java.lang.reflect.Type
import java.util.*

class InternalBackgroundsRepository(private val context: Context, private val gson: Gson) : BackgroundRepository {
    companion object {
        private const val DATA_FILE = "backgrounds.json"
        private val backgroundListType: Type = object : TypeToken<List<Background>>(){}.type
    }

    private var areBackgroundsLoaded = false
    private val backgrounds = mutableListOf<Background>()
    private val backgroundsChangedSubject = PublishSubject.create<Unit>()

    override fun getBackgrounds(): Observable<List<Background>> {
        return getCachedBackgroundsOrLoad()
                .toObservable()
                .concatWith(backgroundsChangedSubject.map { backgrounds })
    }

    override fun getBackground(id: UUID): Maybe<Background> {
        return getCachedBackgroundsOrLoad().flatMapMaybe { backgrounds ->
            backgrounds.find { it.id == id}?.let { background ->
                Maybe.just(background)
            } ?: Maybe.empty()
        }
    }

    override fun addBackground(background: Background) {
        if (background.id == null) {
            val newBackground = background.copy(
                    id = UUID.randomUUID()
            )
            backgrounds.add(newBackground)
        } else {
            val index = backgrounds.indexOfFirst { it.id == background.id }
            if (index >= 0) {
                backgrounds[index] = background
            } else {
                backgrounds.add(background)
            }
        }
        backgroundsChangedSubject.onNext(Unit)
        saveBackgrounds()
    }

    override fun deleteBackground(background: Background): Completable {
        return getCachedBackgroundsOrLoad().doAfterSuccess {
            backgrounds.removeAll {
                it.id == background.id
            }
            backgroundsChangedSubject.onNext(Unit)
            saveBackgrounds()
        }.ignoreElement()
    }

    private fun getCachedBackgroundsOrLoad(): Single<List<Background>> {
        return if (areBackgroundsLoaded) {
            Single.just(backgrounds)
        } else {
            loadBackgrounds().map {
                backgrounds.clear()
                backgrounds.addAll(it)
                areBackgroundsLoaded = true
                backgrounds
            }
        }
    }

    private fun loadBackgrounds(): Single<List<Background>> {
        return Single.create { emitter ->
            val dataFile = File(context.filesDir, DATA_FILE)
            if (!dataFile.isFile) {
                emitter.onSuccess(emptyList())
                return@create
            }

            try {
                val backgrounds = gson.fromJson<List<Background>>(FileReader(dataFile), backgroundListType)
                emitter.onSuccess(backgrounds ?: emptyList())
            } catch (_: Exception) {
                emitter.onSuccess(emptyList())
            }
        }
    }

    private fun saveBackgrounds() {
        val dataFile = File(context.filesDir, DATA_FILE)

        try {
            val layoutsJson = gson.toJson(backgrounds)

            OutputStreamWriter(FileOutputStream(dataFile)).use {
                it.write(layoutsJson)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}