package me.magnum.melonds.domain.repositories

import io.reactivex.Maybe
import io.reactivex.Observable
import me.magnum.melonds.domain.model.AppUpdate

interface UpdatesRepository {
    fun checkNewUpdate(): Maybe<AppUpdate>
    fun skipUpdate(update: AppUpdate)
}