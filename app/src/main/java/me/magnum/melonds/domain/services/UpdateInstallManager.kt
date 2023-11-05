package me.magnum.melonds.domain.services

import kotlinx.coroutines.flow.Flow
import me.magnum.melonds.domain.model.DownloadProgress
import me.magnum.melonds.domain.model.appupdate.AppUpdate

interface UpdateInstallManager {
    fun downloadAndInstallUpdate(update: AppUpdate): Flow<DownloadProgress>
}