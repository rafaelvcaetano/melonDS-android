package me.magnum.melonds.domain.repositories

import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Observable
import me.magnum.melonds.domain.model.Background
import java.util.*

interface BackgroundRepository {
    fun getBackgrounds(): Observable<List<Background>>
    fun getBackground(id: UUID): Maybe<Background>
    fun addBackground(background: Background)
    fun deleteBackground(background: Background): Completable
}