package me.magnum.melonds.parcelables.cheat

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import me.magnum.melonds.domain.model.CheatFolder

@Parcelize
class CheatFolderParcelable(
    val id: Long?,
    val name: String,
    val cheats: List<CheatParcelable>,
) : Parcelable {

    fun toCheatFolder(): CheatFolder {
        return CheatFolder(
            id = id,
            name = name,
            cheats = cheats.map { it.toCheat() }
        )
    }

    companion object {
        fun fromCheatFolder(cheatFolder: CheatFolder): CheatFolderParcelable {
            return CheatFolderParcelable(
                id = cheatFolder.id,
                name = cheatFolder.name,
                cheats = cheatFolder.cheats.map { CheatParcelable.fromCheat(it) }
            )
        }
    }
}