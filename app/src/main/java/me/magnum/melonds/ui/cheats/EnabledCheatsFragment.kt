package me.magnum.melonds.ui.cheats

import me.magnum.melonds.R
import me.magnum.melonds.domain.model.Cheat

class EnabledCheatsFragment : CheatsScreenFragment() {

    override fun getScreenName(): String {
        return getString(R.string.enabled_cheats)
    }

    override fun getCheats(): List<Cheat> {
        return viewModel.getGameSelectedCheats()
    }
}