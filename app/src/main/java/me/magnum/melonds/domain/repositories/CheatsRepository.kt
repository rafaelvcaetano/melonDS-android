package me.magnum.melonds.domain.repositories

import android.net.Uri
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.domain.model.CheatImportProgress
import me.magnum.melonds.domain.model.Game
import me.magnum.melonds.domain.model.RomInfo

interface CheatsRepository {
    fun getAllRomCheats(romInfo: RomInfo): Maybe<List<Game>>
    fun getRomEnabledCheats(romInfo: RomInfo): Single<List<Cheat>>
    fun updateCheatsStatus(cheats: List<Cheat>): Completable
    fun addGameCheats(game: Game)
    fun deleteAllCheats()
    fun importCheats(uri: Uri)
    fun isCheatImportOngoing(): Boolean
    fun getCheatImportProgress(): Observable<CheatImportProgress>
}