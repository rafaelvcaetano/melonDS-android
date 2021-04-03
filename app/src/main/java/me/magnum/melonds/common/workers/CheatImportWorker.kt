package me.magnum.melonds.common.workers

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.Assisted
import androidx.hilt.work.WorkerInject
import androidx.work.ForegroundInfo
import androidx.work.RxWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.reactivex.Single
import me.magnum.melonds.MelonDSApplication
import me.magnum.melonds.R
import me.magnum.melonds.common.cheats.CheatDatabaseParserListener
import me.magnum.melonds.common.cheats.ProgressTrackerInputStream
import me.magnum.melonds.common.cheats.XmlCheatDatabaseParser
import me.magnum.melonds.domain.model.Game
import me.magnum.melonds.domain.repositories.CheatsRepository

class CheatImportWorker @WorkerInject constructor(
        @Assisted appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val cheatsRepository: CheatsRepository
) : RxWorker(appContext, workerParams) {

    companion object {
        const val KEY_URI = "uri"
        const val KEY_PROGRESS_RELATIVE = "progress_relative"
        const val KEY_PROGRESS_ITEM = "progress_item"

        private const val NOTIFICATION_ID_CHEATS_IMPORT = 100
    }

    override fun createWork(): Single<Result> {
        return Single.create { emitter ->
            setForegroundAsync(createForegroundInfo(null, 0, true))

            val uri = inputData.getString(KEY_URI)?.let { Uri.parse(it) }
            if (uri == null) {
                emitter.onSuccess(Result.failure())
                return@create
            }

            try {
                val databaseDocument = DocumentFile.fromSingleUri(applicationContext, uri)
                if (databaseDocument?.isFile != true) {
                    emitter.onSuccess(Result.failure())
                    return@create
                }

                val totalFileSize = applicationContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                    val length = it.length
                    if (length == AssetFileDescriptor.UNKNOWN_LENGTH)
                        null
                    else
                        length
                }

                val databaseExtension = databaseDocument.name?.substringAfterLast('.')
                val parser = when (databaseExtension) {
                    "xml" -> XmlCheatDatabaseParser()
                    else -> {
                        emitter.onSuccess(Result.failure())
                        return@create
                    }
                }

                // Delete all cheats before importing
                cheatsRepository.deleteAllCheats()

                applicationContext.contentResolver.openInputStream(uri)?.use {
                    val progressTrackerStream = ProgressTrackerInputStream(it)
                    parser.parseCheatDatabase(progressTrackerStream, object : CheatDatabaseParserListener {
                        override fun onGameParseStart(gameName: String) {
                            val readProgress = if (totalFileSize != null)
                                (progressTrackerStream.totalReadBytes.toDouble() / totalFileSize * 100).toInt()
                            else
                                0

                            setForegroundAsync(createForegroundInfo(gameName, readProgress, totalFileSize == null))
                            setProgressAsync(workDataOf(
                                    KEY_PROGRESS_RELATIVE to readProgress / 100f,
                                    KEY_PROGRESS_ITEM to gameName
                            ))
                        }

                        override fun onGameParsed(game: Game) {
                            cheatsRepository.addGameCheats(game)
                        }

                        override fun onParseComplete() {
                            emitter.onSuccess(Result.success())
                        }
                    })
                }
            } catch (e: Exception) {
                emitter.onError(e)
            }
        }
    }

    private fun createForegroundInfo(gameName: String?, progress: Int, indeterminate: Boolean): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, MelonDSApplication.NOTIFICATION_ID_CHEAT_IMPORTING)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSubText(applicationContext.getString(R.string.importing_cheats))
                .setContentTitle(gameName ?: "")
                .setColor(ContextCompat.getColor(applicationContext, R.color.melonMain))
                .setSmallIcon(R.drawable.ic_melon_small)
                .setProgress(100, progress, indeterminate)
                .build()

        return ForegroundInfo(NOTIFICATION_ID_CHEATS_IMPORT, notification)
    }

}