package me.magnum.melonds.parcelables.cheat

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import me.magnum.melonds.domain.model.Cheat

@Parcelize
class CheatParcelable(
    val id: Long?,
    val cheatDatabaseId: Long,
    val name: String,
    val description: String?,
    val code: String,
    val enabled: Boolean,
) : Parcelable {

    fun toCheat(): Cheat {
        return Cheat(
            id = id,
            cheatDatabaseId = cheatDatabaseId,
            name = name,
            description = description,
            code = code,
            enabled = enabled,
        )
    }

    companion object {
        fun fromCheat(cheat: Cheat): CheatParcelable {
            return CheatParcelable(
                id = cheat.id,
                cheatDatabaseId = cheat.cheatDatabaseId,
                name = cheat.name,
                description = cheat.description,
                code = cheat.code,
                enabled = cheat.enabled,
            )
        }
    }
}