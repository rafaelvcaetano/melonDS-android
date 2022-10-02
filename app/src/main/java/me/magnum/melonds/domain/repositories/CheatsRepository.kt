package me.magnum.melonds.domain.repositories

import android.net.Uri
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import me.magnum.melonds.domain.model.*

interface CheatsRepository {
    fun getAllRomCheats(romInfo: RomInfo): Maybe<List<Game>>
    fun getRomEnabledCheats(romInfo: RomInfo): Single<List<Cheat>>
    fun updateCheatsStatus(cheats: List<Cheat>): Completable
    fun deleteCheatDatabaseIfExists(databaseName: String)
    fun addCheatDatabase(databaseName: String): CheatDatabase
    fun addGameCheats(databaseId: Long, game: Game)
    fun deleteAllCheats()
    fun importCheats(uri: Uri)
    fun isCheatImportOngoing(): Boolean
    fun getCheatImportProgress(): Observable<CheatImportProgress>
}