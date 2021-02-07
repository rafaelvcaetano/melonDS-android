package me.magnum.melonds.impl

import android.content.Context
import android.net.Uri
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import me.magnum.melonds.database.MelonDatabase
import me.magnum.melonds.database.entities.CheatEntity
import me.magnum.melonds.database.entities.CheatFolderEntity
import me.magnum.melonds.database.entities.CheatStatusUpdate
import me.magnum.melonds.database.entities.GameEntity
import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.domain.model.CheatFolder
import me.magnum.melonds.domain.model.Game
import me.magnum.melonds.domain.model.RomInfo
import me.magnum.melonds.domain.repositories.CheatsRepository

class RoomCheatsRepository(private val context: Context, private val database: MelonDatabase) : CheatsRepository {
    override fun getAllRomCheats(romInfo: RomInfo): Maybe<List<Game>> {
        return database.gameDao().findGameWithCheats(romInfo.gameCode).map {
            it.map { game ->
                Game(
                        game.game.id,
                        game.game.name,
                        game.game.gameCode,
                        game.cheatFolders.map { category ->
                            CheatFolder(
                                    category.cheatFolder.id,
                                    category.cheatFolder.name,
                                    category.cheats.map { cheat ->
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
                )
            }
        }.subscribeOn(Schedulers.io())
    }

    override fun getRomEnabledCheats(romInfo: RomInfo): Single<List<Cheat>> {
        return database.cheatDao().getEnabledRomCheats(romInfo.gameCode).map {
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

    override fun updateCheatsStatus(cheats: List<Cheat>): Completable {
        val cheatEntities = cheats.map {
            CheatStatusUpdate(it.id!!, it.enabled)
        }

        return Completable.create {
            database.cheatDao().updateCheatsStatus(cheatEntities)
            it.onComplete()
        }.subscribeOn(Schedulers.io())
    }

    override fun addGameCheats(game: Game) {
        val gameEntity = GameEntity(
                null,
                game.name,
                game.gameCode
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

        WorkManager.getInstance(context).enqueue(workRequest)
    }
}