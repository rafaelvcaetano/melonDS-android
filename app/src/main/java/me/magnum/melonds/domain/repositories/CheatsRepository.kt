package me.magnum.melonds.domain.repositories

import android.net.Uri
import io.reactivex.Observable
import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.domain.model.CheatDatabase
import me.magnum.melonds.domain.model.CheatFolder
import me.magnum.melonds.domain.model.CheatImportProgress
import me.magnum.melonds.domain.model.Game
import me.magnum.melonds.domain.model.RomInfo

interface CheatsRepository {
    suspend fun getGames(): List<Game>
    suspend fun findGameForRom(romInfo: RomInfo): Game?
    suspend fun getAllGameCheats(game: Game): List<CheatFolder>
    suspend fun getRomEnabledCheats(romInfo: RomInfo): List<Cheat>
    suspend fun updateCheatsStatus(cheats: List<Cheat>)
    fun deleteCheatDatabaseIfExists(databaseName: String)
    fun addCheatDatabase(databaseName: String): CheatDatabase
    fun addGameCheats(databaseId: Long, game: Game)
    fun importCheats(uri: Uri)
    fun isCheatImportOngoing(): Boolean
    fun getCheatImportProgress(): Observable<CheatImportProgress>
}