package me.magnum.melonds.playstore

import io.reactivex.Maybe
import me.magnum.melonds.domain.model.appupdate.AppUpdate
import me.magnum.melonds.domain.repositories.UpdatesRepository

class PlayStoreUpdatesRepository : UpdatesRepository {
    override fun checkNewUpdate(): Maybe<AppUpdate> {
        return Maybe.empty()
    }

    override fun skipUpdate(update: AppUpdate) {
        // Do nothing. Update checking not supported in the Play Store version
    }

    override fun notifyUpdateDownloaded(update: AppUpdate) {
        // Do nothing
    }
}