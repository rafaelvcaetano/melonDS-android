package me.magnum.melonds.common.workers

import android.content.Context
import android.content.pm.ServiceInfo
import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.os.Build
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
import me.magnum.melonds.MelonDSApplication
import me.magnum.melonds.R
import me.magnum.melonds.common.cheats.CheatDatabaseParser
import me.magnum.melonds.common.cheats.CheatDatabaseParserListener
import me.magnum.melonds.common.cheats.ProgressTrackerInputStream
import me.magnum.melonds.common.cheats.XmlCheatDatabaseParser
import me.magnum.melonds.domain.model.CheatDatabase
import me.magnum.melonds.domain.model.Game
import me.magnum.melonds.domain.repositories.CheatsRepository
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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

            val totalFileSize = applicationContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                val length = it.length
                if (length == AssetFileDescriptor.UNKNOWN_LENGTH) {
                    null
                } else {
                    length
                }
            }

            val databaseExtension = databaseDocument.name?.substringAfterLast('.')
            val parser = when (databaseExtension) {
                "xml" -> XmlCheatDatabaseParser()
                else -> {
                    return Result.failure()
                }
            }

            return parseXmlDocument(uri, parser, totalFileSize)
        } catch (e: Exception) {
            return Result.failure()
        }
    }

    private suspend fun parseXmlDocument(uri: Uri, parser: CheatDatabaseParser, totalFileSize: Long?) = suspendCoroutine { continuation ->
        applicationContext.contentResolver.openInputStream(uri)?.use {
            val progressTrackerStream = ProgressTrackerInputStream(it)
            parser.parseCheatDatabase(progressTrackerStream, object : CheatDatabaseParserListener {
                private var cheatDatabase: CheatDatabase? = null

                override fun onDatabaseParseStart(databaseName: String) {
                    cheatsRepository.deleteCheatDatabaseIfExists(databaseName)
                    cheatDatabase = cheatsRepository.addCheatDatabase(databaseName)
                }

                override fun onGameParseStart(gameName: String) {
                    val readProgress = if (totalFileSize != null) {
                        (progressTrackerStream.totalReadBytes.toDouble() / totalFileSize * 100).toInt()
                    } else {
                        0
                    }

                    setForegroundAsync(createForegroundInfo(gameName, readProgress, totalFileSize == null))
                    setProgressAsync(workDataOf(
                        KEY_PROGRESS_RELATIVE to readProgress / 100f,
                        KEY_PROGRESS_ITEM to gameName
                    ))
                }

                override fun onGameParsed(game: Game) {
                    cheatDatabase?.id?.let {
                        cheatsRepository.addGameCheats(it, game)
                    }
                }

                override fun onParseComplete() {
                    continuation.resume(Result.success())
                }
            })
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