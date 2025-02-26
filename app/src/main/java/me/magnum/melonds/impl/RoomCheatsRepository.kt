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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
import me.magnum.melonds.ui.cheats.model.CheatSubmissionForm

class RoomCheatsRepository(private val context: Context, private val database: MelonDatabase) : CheatsRepository {
    companion object {
        private const val IMPORT_WORKER_NAME = "cheat_import_worker"
    }

    override suspend fun getGames(): List<Game> {
        return database.gameDao().getGames().map { game ->
            Game(
                game.id,
                game.name,
                game.gameCode,
                game.gameChecksum,
                emptyList(),
            )
        }
    }

    override suspend fun findGameForRom(romInfo: RomInfo): Game? {
        return database.gameDao().findGame(romInfo.gameCode, romInfo.headerChecksumString())?.let {
            Game(
                it.id,
                it.name,
                it.gameCode,
                it.gameChecksum,
                emptyList(),
            )
        }
    }

    override fun getAllGameCheats(game: Game): Flow<List<CheatFolder>> {
        val gameId = game.id ?: return emptyFlow()

        return database.gameDao().getGameCheats(gameId).map { foldersWithCheats ->
            foldersWithCheats.map {
                CheatFolder(
                    it.cheatFolder.id,
                    it.cheatFolder.name,
                    it.cheats.map { cheat ->
                        Cheat(
                            cheat.id,
                            cheat.cheatDatabaseId,
                            cheat.name,
                            cheat.description,
                            cheat.code,
                            cheat.enabled
                        )
                    }
                )
            }
        }
    }

    override fun getFolderCheats(folder: CheatFolder): Flow<List<Cheat>> {
        return database.cheatDao().getFolderCheats(folder.id!!).map {
            it.map { cheat ->
                Cheat(
                    cheat.id,
                    cheat.cheatDatabaseId,
                    cheat.name,
                    cheat.description,
                    cheat.code,
                    cheat.enabled
                )
            }
        }
    }

    override suspend fun getRomEnabledCheats(romInfo: RomInfo): List<Cheat> {
        return database.cheatDao().getEnabledRomCheats(romInfo.gameCode, romInfo.headerChecksumString()).map { cheat ->
            Cheat(
                cheat.id,
                cheat.cheatDatabaseId,
                cheat.name,
                cheat.description,
                cheat.code,
                cheat.enabled
            )
        }
    }

    override suspend fun updateCheatsStatus(cheats: List<Cheat>) {
        val cheatEntities = cheats.map {
            CheatStatusUpdate(it.id!!, it.enabled)
        }

        database.cheatDao().updateCheatsStatus(cheatEntities)
    }

    override suspend fun addCheatFolder(folderName: String, game: Game) {
        val gameId = if (game.id == null) {
            // It's a new game. Insert it first
            val gameEntity = GameEntity(
                id = null,
                name = game.name,
                gameCode = game.gameCode,
                gameChecksum = game.gameChecksum
            )
            database.gameDao().insertGame(gameEntity)
        } else {
            game.id
        }

        val cheatFolderEntity = CheatFolderEntity(null, gameId, folderName)
        database.cheatFolderDao().insertCheatFolder(cheatFolderEntity)
    }

    override suspend fun deleteCheatDatabaseIfExists(databaseName: String) {
        if (databaseName == CheatDatabaseEntity.CUSTOM_CHEATS_DATABASE_NAME) {
            // Don't allow the custom cheat database to be deleted
            return
        }

        database.cheatDatabaseDao().deleteCheatDatabase(databaseName)
        database.cheatFolderDao().deleteEmptyFolders()
        database.gameDao().deleteEmptyGames()
    }

    override suspend fun addCheatDatabase(databaseName: String): CheatDatabase {
        val cheatDatabaseEntity = CheatDatabaseEntity(
            null,
            databaseName,
        )

        val databaseId = database.cheatDatabaseDao().insertCheatDatabase(cheatDatabaseEntity)
        return CheatDatabase(databaseId, databaseName)
    }

    override suspend fun addGameCheats(game: Game): Game {
        val gameEntity = GameEntity(
            null,
            game.name,
            game.gameCode,
            game.gameChecksum
        )

        // Insertion may do nothing if the game already exists
        database.gameDao().insertGame(gameEntity)
        val insertedGame = database.gameDao().findGame(game.gameCode, game.gameChecksum)!!
        val gameId = insertedGame.id!!

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
                    id = null,
                    cheatFolderId = pair.second,
                    cheatDatabaseId = it.cheatDatabaseId,
                    name = it.name,
                    description = it.description,
                    code = it.code,
                    enabled = false
                )
            }
        }
        database.cheatDao().insertCheats(cheatEntities)

        return Game(
            id = insertedGame.id,
            name = insertedGame.name,
            gameCode = insertedGame.gameCode,
            gameChecksum = insertedGame.gameChecksum,
            cheats = emptyList(),
        )
    }

    override suspend fun addCheat(folder: CheatFolder, cheat: Cheat) {
        val cheatEntity = CheatEntity(
            id = null,
            cheatFolderId = folder.id!!,
            cheatDatabaseId = cheat.cheatDatabaseId,
            name = cheat.name,
            description = cheat.description,
            code = cheat.code,
            enabled = cheat.enabled,
        )

        database.cheatDao().insertCheat(cheatEntity)
    }

    override suspend fun addCustomCheat(folder: CheatFolder, cheatForm: CheatSubmissionForm) {
        val cheatEntity = CheatEntity(
            id = null,
            cheatFolderId = folder.id!!,
            cheatDatabaseId = CheatDatabaseEntity.CUSTOM_CHEATS_DATABASE_ID,
            name = cheatForm.name,
            description = cheatForm.description,
            code = cheatForm.code,
            enabled = false,
        )

        database.cheatDao().insertCheat(cheatEntity)
    }

    override suspend fun updateCheat(cheat: Cheat) {
        val originalEntity = database.cheatDao().getCheat(cheat.id!!) ?: return
        val updatedCheatEntity = CheatEntity(
            id = cheat.id,
            cheatFolderId = originalEntity.cheatFolderId,
            cheatDatabaseId = originalEntity.cheatDatabaseId,
            name = cheat.name,
            description = cheat.description,
            code = cheat.code,
            enabled = cheat.enabled,
        )

        database.cheatDao().insertCheat(updatedCheatEntity)
    }

    override suspend fun deleteCheat(cheat: Cheat) {
        val cheatId = cheat.id ?: return
        database.cheatDao().deleteCheat(cheatId)
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