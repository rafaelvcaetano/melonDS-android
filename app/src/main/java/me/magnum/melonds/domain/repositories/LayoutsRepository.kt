package me.magnum.melonds.domain.repositories

import io.reactivex.Single
import me.magnum.melonds.domain.model.LayoutConfiguration

interface LayoutsRepository {
    fun getLayouts(): Single<List<LayoutConfiguration>>
    fun saveLayout(layout: LayoutConfiguration)
}