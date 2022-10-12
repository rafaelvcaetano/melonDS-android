package me.magnum.melonds.github.services

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import androidx.core.content.getSystemService
import io.reactivex.Observable
import me.magnum.melonds.common.providers.UpdateContentProvider
import me.magnum.melonds.domain.model.AppUpdate
import me.magnum.melonds.domain.model.DownloadProgress
import me.magnum.melonds.domain.services.UpdateInstallManager
import java.io.File

class GitHubUpdateInstallManager(private val context: Context) : UpdateInstallManager {
    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: return
            if (id == pendingDownloadId) {
                onUpdateDownloaded(id)
                pendingDownloadId = null
            }
        }
    }

    private var pendingDownloadId: Long? = null

    init {
        context.registerReceiver(downloadCompleteReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    override fun downloadAndInstallUpdate(update: AppUpdate): Observable<DownloadProgress> {
        return Observable.create { emitter ->
            val updatesFolder = context.externalCacheDir?.let { File(it, "updates") }
            if (updatesFolder == null) {
                emitter.onComplete()
                return@create
            }

            if (!updatesFolder.isDirectory && !updatesFolder.mkdirs()) {
                emitter.onComplete()
                return@create
            }

            val destinationFile = File(updatesFolder, "update.apk")
            if (destinationFile.isFile) {
                destinationFile.delete()
            }

            val destinationUri = UpdateContentProvider.getUpdateFileUri(context, destinationFile)
            val downloadManager = context.getSystemService<DownloadManager>()!!
            val request = DownloadManager.Request(update.downloadUri).apply {
                setDestinationUri(destinationUri)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setMimeType("application/vnd.android.package-archive")
                setTitle("Downloading update ${update.newVersion}...")
            }
            val downloadId = downloadManager.enqueue(request)
            pendingDownloadId = downloadId

            val downloadUri = Uri.parse("content://downloads/my_downloads/${downloadId}")
            context.contentResolver.registerContentObserver(downloadUri, false, object : ContentObserver(null) {
                init {
                    emitter.setCancellable {
                        context.contentResolver.unregisterContentObserver(this)
                    }
                }

                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    val query = DownloadManager.Query().apply {
                        setFilterById(downloadId)
                    }

                    val cursor = downloadManager.query(query)
                    if (cursor.moveToNext()) {
                        val sizeIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        val downloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)

                        val size = cursor.getLong(sizeIndex)
                        val downloaded = cursor.getLong(downloadedIndex)
                        val status = cursor.getInt(statusIndex)

                        val isFinished = status == DownloadManager.STATUS_FAILED || status == DownloadManager.STATUS_SUCCESSFUL

                        if (size >= 0) {
                            emitter.onNext(DownloadProgress.DownloadUpdate(size, downloaded))
                        }

                        if (isFinished) {
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                emitter.onNext(DownloadProgress.DownloadComplete)
                            } else {
                                emitter.onNext(DownloadProgress.DownloadFailed)
                            }

                            emitter.onComplete()
                            context.contentResolver.unregisterContentObserver(this)
                        }
                    }
                }
            })
        }
    }

    private fun onUpdateDownloaded(downloadId: Long) {
        val downloadManager = context.getSystemService<DownloadManager>()!!
        val fileUri = downloadManager.getUriForDownloadedFile(downloadId)
        val mimeType = downloadManager.getMimeTypeForDownloadedFile(downloadId)

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, mimeType)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(installIntent)
    }
}