package me.magnum.melonds.ui.cheats.model

import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.domain.model.CheatFolder

data class DeletedCheat(val cheat: Cheat, val folder: CheatFolder)