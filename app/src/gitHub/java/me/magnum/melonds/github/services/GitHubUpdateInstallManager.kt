package me.magnum.melonds.github.services

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.DownloadProgress
import me.magnum.melonds.domain.model.appupdate.AppUpdate
import me.magnum.melonds.domain.services.UpdateInstallManager
import me.magnum.melonds.extensions.parcelable
import java.io.File
import java.io.FileInputStream

class GitHubUpdateInstallManager(private val context: Context) : UpdateInstallManager {

    companion object {
        private const val EXTRA_DOWNLOAD_ID = "download_id"
        private const val INSTALL_SESSION_NAME = "melonds_update"
    }

    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: return
            if (id == pendingDownloadId) {
                onUpdateDownloaded(id)
                pendingDownloadId = null
            }
        }
    }

    private val installResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent == null) {
                return
            }

            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1)

            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    intent.parcelable<Intent>(Intent.EXTRA_INTENT)?.let {
                        it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        // Since the broadcast receiver is not exported, only the system can send intents to it, so there's no need to sanitize the intents
                        @SuppressLint("UnsafeIntentLaunch")
                        context.startActivity(it)
                    }
                }
                PackageInstaller.STATUS_SUCCESS -> {
                    // Remove download after installation is successful
                    if (downloadId != -1L) {
                        context.getSystemService<DownloadManager>()?.remove(downloadId)
                    }
                }
                else -> { /* no-op */ }
            }
        }
    }

    private var pendingDownloadId: Long? = null

    init {
        ContextCompat.registerReceiver(
            context,
            downloadCompleteReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
        ContextCompat.registerReceiver(
            context,
            installResultReceiver,
            IntentFilter(getInstallResultAction()),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun downloadAndInstallUpdate(update: AppUpdate): Flow<DownloadProgress> = flow {
        val updatesFolder = context.externalCacheDir?.let { File(it, "updates") }
        if (updatesFolder == null) {
            emit(DownloadProgress.DownloadFailed)
            return@flow
        }

        if (!updatesFolder.isDirectory && !updatesFolder.mkdirs()) {
            emit(DownloadProgress.DownloadFailed)
            return@flow
        }

        val destinationFile = File(updatesFolder, "update.apk")
        if (destinationFile.isFile) {
            destinationFile.delete()
        }

        val destinationUri = Uri.fromFile(destinationFile)
        val downloadManager = context.getSystemService<DownloadManager>()!!
        val request = DownloadManager.Request(update.downloadUri).apply {
            setDestinationUri(destinationUri)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setMimeType("application/vnd.android.package-archive")
            setTitle(context.getString(R.string.downloading_update_version, update.newVersion))
        }
        val downloadId = downloadManager.enqueue(request)
        pendingDownloadId = downloadId

        startDownload(downloadManager, downloadId).collect(this)
    }

    private fun startDownload(downloadManager: DownloadManager, downloadId: Long): Flow<DownloadProgress> = callbackFlow {
        val downloadContentObserver = object : ContentObserver(null) {

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                val query = DownloadManager.Query().apply {
                    setFilterById(downloadId)
                }

                downloadManager.query(query).use { cursor ->
                    if (cursor.moveToNext()) {
                        val sizeIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        val downloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)

                        val size = cursor.getLong(sizeIndex)
                        val downloaded = cursor.getLong(downloadedIndex)
                        val status = cursor.getInt(statusIndex)

                        val isFinished = status == DownloadManager.STATUS_FAILED || status == DownloadManager.STATUS_SUCCESSFUL

                        if (size >= 0) {
                            channel.trySend(DownloadProgress.DownloadUpdate(size, downloaded))
                        }

                        if (isFinished) {
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                channel.trySend(DownloadProgress.DownloadComplete)
                            } else {
                                channel.trySend(DownloadProgress.DownloadFailed)
                            }

                            channel.close()
                        }
                    }
                }
            }
        }

        val downloadUri = "content://downloads/my_downloads/${downloadId}".toUri()
        context.contentResolver.registerContentObserver(downloadUri, false, downloadContentObserver)

        awaitClose {
            context.contentResolver.unregisterContentObserver(downloadContentObserver)
        }
    }

    private fun onUpdateDownloaded(downloadId: Long) {
        val downloadManager = context.getSystemService<DownloadManager>()!!
        val packageInstaller = context.packageManager.packageInstaller

        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = packageInstaller.createSession(params)

        try {
            packageInstaller.openSession(sessionId).use { session ->
                downloadManager.openDownloadedFile(downloadId).use { pfd ->
                    FileInputStream(pfd.fileDescriptor).use { input ->
                        session.openWrite(INSTALL_SESSION_NAME, 0, -1).use { output ->
                            input.copyTo(output)
                            session.fsync(output)
                        }
                    }
                }

                val statusIntent = Intent(getInstallResultAction()).apply {
                    setPackage(context.packageName)
                    putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                }
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pendingIntent = PendingIntent.getBroadcast(context, sessionId, statusIntent, flags)

                @SuppressLint("RequestInstallPackagesPolicy")
                session.commit(pendingIntent.intentSender)
            }
        } catch (_: Exception) {
            packageInstaller.abandonSession(sessionId)
        }
    }

    private fun getInstallResultAction(): String {
        return "${context.packageName}.INSTALL_RESULT"
    }
}