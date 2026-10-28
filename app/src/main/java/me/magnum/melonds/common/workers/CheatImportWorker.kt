package me.magnum.melonds.common.workers

import android.content.Context
import android.content.pm.ServiceInfo
import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import me.magnum.melonds.MelonDSApplication
import me.magnum.melonds.R
import me.magnum.melonds.common.cheats.CheatDatabaseParser
import me.magnum.melonds.common.cheats.importEvents
import me.magnum.melonds.common.cheats.ProgressTrackerInputStream
import me.magnum.melonds.common.cheats.UsrCheatDatabaseParser
import me.magnum.melonds.common.cheats.XmlCheatDatabaseParser
import me.magnum.melonds.domain.repositories.CheatsRepository

@HiltWorker
class CheatImportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val cheatsRepository: CheatsRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_URI = "uri"
        const val KEY_PROGRESS_RELATIVE = "progress_relative"
        const val KEY_PROGRESS_ITEM = "progress_item"

        private const val NOTIFICATION_ID_CHEATS_IMPORT = 100
    }

    override suspend fun doWork(): Result {
        setForeground(createForegroundInfo(null, 0, true))

        val uri = inputData.getString(KEY_URI)?.toUri() ?: return Result.failure()

        try {
            val databaseDocument = DocumentFile.fromSingleUri(applicationContext, uri)
            if (databaseDocument?.isFile != true) {
                return Result.failure()
            }

            val databaseExtension = databaseDocument.name?.substringAfterLast('.')
            val parser = when (databaseExtension) {
                "xml" -> XmlCheatDatabaseParser()
                "dat" -> UsrCheatDatabaseParser()
                else -> {
                    return Result.failure()
                }
            }

            return parseCheatDatabase(uri, parser)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure()
        }
    }

    private suspend fun parseCheatDatabase(uri: Uri, parser: CheatDatabaseParser): Result = coroutineScope {
        val cancellationSignal = CancellationSignal()
        val parsing = async(Dispatchers.IO) {
            try {
                applicationContext.contentResolver.openAssetFileDescriptor(uri, "r", cancellationSignal).use { descriptor ->
                    // A provider may still return a descriptor after it receives cancellation.
                    ensureActive()
                    if (descriptor == null) return@async Result.failure()
                    val totalFileSize = descriptor.length.takeUnless { it == AssetFileDescriptor.UNKNOWN_LENGTH }
                    descriptor.createInputStream().use { input ->
                        val progressTrackerStream = ProgressTrackerInputStream(input)
                        cheatsRepository.replaceImportedCheats(parser.importEvents(progressTrackerStream) { gameName ->
                            val readProgress = if (totalFileSize != null && totalFileSize > 0) {
                                (progressTrackerStream.totalReadBytes.toDouble() / totalFileSize * 100).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }
                            setForegroundAsync(createForegroundInfo(gameName, readProgress, totalFileSize == null))
                            setProgressAsync(workDataOf(
                                KEY_PROGRESS_RELATIVE to readProgress / 100f,
                                KEY_PROGRESS_ITEM to gameName
                            ))
                        })
                    }
                }
                Result.success()
            } catch (error: Exception) {
                // Preserve coroutine cancellation when the provider reports its own exception.
                ensureActive()
                throw error
            }
        }
        try {
            parsing.await()
        } finally {
            // Signal a blocked open before coroutineScope waits for its child to finish.
            cancellationSignal.cancel()
        }
    }

    private fun createForegroundInfo(gameName: String?, progress: Int, indeterminate: Boolean): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, MelonDSApplication.NOTIFICATION_CHANNEL_ID_BACKGROUND_TASKS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSubText(applicationContext.getString(R.string.importing_cheats))
                .setContentTitle(gameName ?: "")
                .setColor(ContextCompat.getColor(applicationContext, R.color.melonMain))
                .setSmallIcon(R.drawable.ic_melon_small)
                .setProgress(100, progress, indeterminate)
                .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIFICATION_ID_CHEATS_IMPORT, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID_CHEATS_IMPORT, notification)
        }
    }
}
