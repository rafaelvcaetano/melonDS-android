package me.magnum.melonds.impl

import android.content.Context
import android.net.Uri
import androidx.lifecycle.Observer
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.magnum.melonds.common.workers.CheatImportWorker
import me.magnum.melonds.database.MelonDatabase
import me.magnum.melonds.database.entities.CheatDatabaseEntity
import me.magnum.melonds.database.entities.CheatEntity
import me.magnum.melonds.database.entities.CheatFolderEntity
import me.magnum.melonds.database.entities.CheatStatusUpdate
import me.magnum.melonds.database.entities.GameEntity
import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.domain.model.CheatDatabase
import me.magnum.melonds.domain.model.CheatFolder
import me.magnum.melonds.domain.model.CheatImportProgress
import me.magnum.melonds.domain.model.Game
import me.magnum.melonds.domain.model.RomInfo
import me.magnum.melonds.domain.repositories.CheatsRepository

class RoomCheatsRepository(private val context: Context, private val database: MelonDatabase) : CheatsRepository {
    companion object {
        private const val IMPORT_WORKER_NAME = "cheat_import_worker"
    }

    override suspend fun observeGames(): Flow<List<Game>> {
        return database.gameDao().getGames().map {
            it.map { game ->
                Game(
                    game.id,
                    game.name,
                    game.gameCode,
                    game.gameChecksum,
                    emptyList(),
                )
            }
        }
    }

    override suspend fun findGamesForRom(romInfo: RomInfo): List<Game> {
        return database.gameDao().findGames(romInfo.gameCode, romInfo.headerChecksumString()).map {
            Game(
                it.id,
                it.name,
                it.gameCode,
                it.gameChecksum,
                emptyList(),
            )
        }
    }

    override suspend fun getAllGameCheats(game: Game): List<CheatFolder> {
        val gameId = game.id ?: return emptyList()

        return database.gameDao().getGameCheats(gameId).map {
            CheatFolder(
                it.cheatFolder.id,
                it.cheatFolder.name,
                it.cheats.map { cheat ->
                    Cheat(
                        cheat.id,
                        cheat.name,
                        cheat.description,
                        cheat.code,
                        cheat.enabled
                    )
                }
            )
        }
    }

    override fun getRomEnabledCheats(romInfo: RomInfo): Single<List<Cheat>> {
        return database.cheatDao().getEnabledRomCheats(romInfo.gameCode, romInfo.headerChecksumString()).map {
            it.map { cheat ->
                Cheat(
                        cheat.id,
                        cheat.name,
                        cheat.description,
                        cheat.code,
                        cheat.enabled
                )
            }
        }.subscribeOn(Schedulers.io())
    }

    override suspend fun updateCheatsStatus(cheats: List<Cheat>) {
        val cheatEntities = cheats.map {
            CheatStatusUpdate(it.id!!, it.enabled)
        }

        database.cheatDao().updateCheatsStatus(cheatEntities)
    }

    override fun deleteCheatDatabaseIfExists(databaseName: String) {
        database.cheatDatabaseDao().deleteCheatDatabase(databaseName)
    }

    override fun addCheatDatabase(databaseName: String): CheatDatabase {
        val cheatDatabaseEntity = CheatDatabaseEntity(
            null,
            databaseName,
        )

        val databaseId = database.cheatDatabaseDao().insertCheatDatabase(cheatDatabaseEntity)
        return CheatDatabase(databaseId, databaseName)
    }

    override fun addGameCheats(databaseId: Long, game: Game) {
        val gameEntity = GameEntity(
                null,
                databaseId,
                game.name,
                game.gameCode,
                game.gameChecksum
        )

        val gameId = database.gameDao().insertGame(gameEntity)
        val categoryEntities = game.cheats.map { category ->
            CheatFolderEntity(
                    null,
                    gameId,
                    category.name
            )
        }
        val categoryIds = database.cheatFolderDao().insertCheatFolders(categoryEntities)

        val cheatEntities = game.cheats.zip(categoryIds).flatMap { pair ->
            pair.first.cheats.map {
                CheatEntity(
                        null,
                        pair.second,
                        it.name,
                        it.description,
                        it.code,
                        false
                )
            }
        }
        database.cheatDao().insertCheats(cheatEntities)
    }

    override fun deleteAllCheats() {
        database.gameDao().deleteAll()
    }

    override fun importCheats(uri: Uri) {
        val workRequest = OneTimeWorkRequestBuilder<CheatImportWorker>()
                .setInputData(workDataOf(CheatImportWorker.KEY_URI to uri.toString()))
                .build()

        WorkManager.getInstance(context).enqueueUniqueWork(IMPORT_WORKER_NAME, ExistingWorkPolicy.KEEP, workRequest)
    }

    override fun isCheatImportOngoing(): Boolean {
        val workManager = WorkManager.getInstance(context)
        val statuses = workManager.getWorkInfosForUniqueWork(IMPORT_WORKER_NAME)
        val infos = statuses.get()

        return infos.any { !it.state.isFinished }
    }

    override fun getCheatImportProgress(): Observable<CheatImportProgress> {
        return Observable.create { emitter ->
            val workManager = WorkManager.getInstance(context)
            val statuses = workManager.getWorkInfosForUniqueWork(IMPORT_WORKER_NAME)
            val infos = statuses.get()

            if (infos.all { it.state.isFinished }) {
                emitter.onNext(CheatImportProgress(CheatImportProgress.CheatImportStatus.NOT_IMPORTING, 0f, null))
                emitter.onComplete()
            } else {
                val observer = Observer<MutableList<WorkInfo>> {
                    val workInfo = it.firstOrNull()
                    if (workInfo != null) {
                        when (workInfo.state) {
                            WorkInfo.State.ENQUEUED -> CheatImportProgress(CheatImportProgress.CheatImportStatus.STARTING, 0f, null)
                            WorkInfo.State.RUNNING -> {
                                val relativeProgress = workInfo.progress.getFloat(CheatImportWorker.KEY_PROGRESS_RELATIVE, 0f)
                                val itemName = workInfo.progress.getString(CheatImportWorker.KEY_PROGRESS_ITEM)
                                CheatImportProgress(CheatImportProgress.CheatImportStatus.ONGOING, relativeProgress, itemName)
                            }
                            WorkInfo.State.SUCCEEDED -> CheatImportProgress(CheatImportProgress.CheatImportStatus.FINISHED, 1f, null)
                            WorkInfo.State.CANCELLED,
                            WorkInfo.State.FAILED -> CheatImportProgress(CheatImportProgress.CheatImportStatus.FAILED, 0f, null)
                            else -> null
                        }?.let { progress ->
                            emitter.onNext(progress)
                            if (progress.status == CheatImportProgress.CheatImportStatus.FAILED || progress.status == CheatImportProgress.CheatImportStatus.FINISHED) {
                                emitter.onComplete()
                            }
                        }
                    }
                }

                val workInfosLiveData = workManager.getWorkInfosForUniqueWorkLiveData(IMPORT_WORKER_NAME)
                workInfosLiveData.observeForever(observer)

                emitter.setCancellable {
                    workInfosLiveData.removeObserver(observer)
                }
            }
        }
    }
}