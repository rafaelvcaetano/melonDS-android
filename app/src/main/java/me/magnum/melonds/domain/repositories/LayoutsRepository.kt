package me.magnum.melonds.domain.repositories

import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import me.magnum.melonds.domain.model.LayoutConfiguration
import java.util.*

interface LayoutsRepository {
    fun getLayouts(): Observable<List<LayoutConfiguration>>
    fun getLayout(id: UUID): Maybe<LayoutConfiguration>
    fun saveLayout(layout: LayoutConfiguration)
}