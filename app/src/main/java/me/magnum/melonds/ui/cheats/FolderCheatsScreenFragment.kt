package me.magnum.melonds.ui.cheats

import me.magnum.melonds.domain.model.Cheat

class FolderCheatsScreenFragment : CheatsScreenFragment() {

    override fun getScreenName(): String? {
        return viewModel.getSelectedFolder().value?.name
    }

    override fun getCheats(): List<Cheat> {
        return viewModel.getSelectedFolderCheats()
    }
}