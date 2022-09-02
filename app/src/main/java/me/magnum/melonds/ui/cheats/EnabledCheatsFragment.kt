package me.magnum.melonds.ui.cheats

import me.magnum.melonds.R
import me.magnum.melonds.domain.model.Cheat

class EnabledCheatsFragment : CheatsScreenFragment() {

    override fun getScreenName(): String {
        return getString(R.string.enabled_cheats)
    }

    override fun getNoContentText(): String {
        return getString(R.string.no_enabled_cheats_for_rom)
    }

    override fun getCheats(): List<Cheat> {
        return viewModel.getGameSelectedCheats()
    }
}