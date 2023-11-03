package me.magnum.melonds.domain.services

import io.reactivex.Observable
import me.magnum.melonds.domain.model.appupdate.AppUpdate
import me.magnum.melonds.domain.model.DownloadProgress

interface UpdateInstallManager {
    fun downloadAndInstallUpdate(update: AppUpdate): Observable<DownloadProgress>
}