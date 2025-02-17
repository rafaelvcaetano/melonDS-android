package me.magnum.melonds.parcelables.cheat

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import me.magnum.melonds.domain.model.Game

@Parcelize
class GameParcelable(
    val id: Long?,
    val name: String,
    val gameCode: String,
    val gameChecksum: String?,
    val cheats: List<CheatFolderParcelable>,
) : Parcelable {

    fun toGame(): Game {
        return Game(
            id = id,
            name = name,
            gameCode = gameCode,
            gameChecksum = gameChecksum,
            cheats = cheats.map { it.toCheatFolder() },
        )
    }

    companion object {
        fun fromGame(game: Game): GameParcelable {
            return GameParcelable(
                id = game.id,
                name = game.name,
                gameCode = game.gameCode,
                gameChecksum = game.gameChecksum,
                cheats = game.cheats.map { CheatFolderParcelable.fromCheatFolder(it) },
            )
        }
    }
}