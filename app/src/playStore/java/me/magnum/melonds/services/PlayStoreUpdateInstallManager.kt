package me.magnum.melonds.services

import io.reactivex.Observable
import me.magnum.melonds.domain.model.AppUpdate
import me.magnum.melonds.domain.model.DownloadProgress
import me.magnum.melonds.domain.services.UpdateInstallManager

class PlayStoreUpdateInstallManager : UpdateInstallManager {
    override fun downloadAndInstallUpdate(update: AppUpdate): Observable<DownloadProgress> {
        return Observable.error(UnsupportedOperationException("Cannot automatically update from PlayStore"))
    }
}