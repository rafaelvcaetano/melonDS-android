package me.magnum.melonds.domain.repositories

import io.reactivex.Maybe
import me.magnum.melonds.domain.model.appupdate.AppUpdate

interface UpdatesRepository {
    fun checkNewUpdate(): Maybe<AppUpdate>
    fun skipUpdate(update: AppUpdate)
    fun notifyUpdateDownloaded(update: AppUpdate)
}