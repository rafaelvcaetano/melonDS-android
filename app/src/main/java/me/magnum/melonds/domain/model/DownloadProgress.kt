package me.magnum.melonds.domain.model

sealed class DownloadProgress {
    class DownloadUpdate(val totalSize: Long, val downloadedBytes: Long) : DownloadProgress()
    object DownloadComplete : DownloadProgress()
    object DownloadFailed : DownloadProgress()
}