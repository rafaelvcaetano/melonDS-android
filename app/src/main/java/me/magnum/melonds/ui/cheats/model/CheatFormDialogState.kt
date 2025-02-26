package me.magnum.melonds.ui.cheats.model

import androidx.compose.runtime.saveable.listSaver
import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.parcelables.cheat.CheatParcelable

sealed class CheatFormDialogState {
    data object Hidden : CheatFormDialogState()
    data object NewCheat : CheatFormDialogState()
    data class EditCheat(val cheat: Cheat) : CheatFormDialogState()

    companion object {
        private const val TYPE_HIDDEN = 0
        private const val TYPE_NEW_CHET = 1
        private const val TYPE_EDIT_CHEAT = 2

        val Saver = listSaver(
            save = {
                buildList {
                    val type = when (it) {
                        Hidden -> TYPE_HIDDEN
                        NewCheat -> TYPE_NEW_CHET
                        is EditCheat -> TYPE_EDIT_CHEAT
                    }
                    add(type)

                    if (it is EditCheat) {
                        add(CheatParcelable.fromCheat(it.cheat))
                    }
                }
            },
            restore = {
                val stateType = it[0] as? Int
                when (stateType) {
                    TYPE_HIDDEN -> Hidden
                    TYPE_NEW_CHET -> NewCheat
                    TYPE_EDIT_CHEAT -> EditCheat((it[1] as CheatParcelable).toCheat())
                    else -> null
                }
            }
        )
    }
}