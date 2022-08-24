package me.magnum.melonds.ui.cheats

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.disposables.CompositeDisposable
import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.domain.model.CheatFolder
import me.magnum.melonds.domain.model.Game
import me.magnum.melonds.domain.model.RomInfo
import me.magnum.melonds.domain.repositories.CheatsRepository
import me.magnum.melonds.extensions.addTo
import me.magnum.melonds.utils.SingleLiveEvent
import javax.inject.Inject

@HiltViewModel
class CheatsViewModel @Inject constructor(private val cheatsRepository: CheatsRepository) : ViewModel() {
    private var selectedGame = MutableLiveData<Game>()
    private var selectedFolder = MutableLiveData<CheatFolder>()
    private var allRomCheatsLiveData: MutableLiveData<List<Game>>? = null
    private val committingCheatsChangesStatusLiveData = MutableLiveData(false)
    private val cheatChangesCommittedLiveEvent = SingleLiveEvent<Unit>()
    private val modifiedCheatSet = mutableListOf<Cheat>()

    private val disposables = CompositeDisposable()

    fun getRomCheats(romInfo: RomInfo): LiveData<List<Game>> {
        if (allRomCheatsLiveData != null) {
            return allRomCheatsLiveData!!
        }

        allRomCheatsLiveData = MutableLiveData()
        cheatsRepository.getAllRomCheats(romInfo).subscribe {
            allRomCheatsLiveData!!.postValue(it)
        }.addTo(disposables)

        return allRomCheatsLiveData!!
    }

    fun getGames(): List<Game> {
        return allRomCheatsLiveData?.value ?: emptyList()
    }

    fun getSelectedGame(): LiveData<Game> {
        return selectedGame
    }

    fun setSelectedGame(game: Game) {
        selectedGame.value = game
    }

    fun getSelectedFolder(): LiveData<CheatFolder> {
        return selectedFolder
    }

    fun setSelectedFolder(folder: CheatFolder) {
        selectedFolder.value = folder
    }

    fun getSelectedFolderCheats(): List<Cheat> {
        val cheats = selectedFolder.value?.cheats?.toMutableList() ?: mutableListOf()
        if (cheats.isEmpty() || modifiedCheatSet.isEmpty()) {
            return cheats
        }

        modifiedCheatSet.forEach { cheat ->
            val originalCheatIndex = cheats.indexOfFirst { it.id == cheat.id }
            if (originalCheatIndex >= 0) {
                cheats[originalCheatIndex] = cheat
            }
        }
        return cheats
    }

    fun notifyCheatEnabledStatusChanged(cheat: Cheat, isEnabled: Boolean) {
        // Compare new status with the original one and take action accordingly
        if (isEnabled == cheat.enabled) {
            modifiedCheatSet.removeAll { it.id == cheat.id }
        } else {
            modifiedCheatSet.add(cheat.copy(enabled = isEnabled))
        }
    }

    fun committingCheatsChangesStatus(): LiveData<Boolean> {
        return committingCheatsChangesStatusLiveData
    }

    fun onCheatChangesCommitted(): LiveData<Unit> {
        return cheatChangesCommittedLiveEvent
    }

    fun commitCheatChanges(): LiveData<Boolean> {
        val liveData = MutableLiveData<Boolean>()

        if (modifiedCheatSet.isEmpty()) {
            cheatChangesCommittedLiveEvent.postValue(Unit)
            return liveData
        }

        committingCheatsChangesStatusLiveData.value = true
        cheatsRepository.updateCheatsStatus(modifiedCheatSet).doAfterTerminate {
            committingCheatsChangesStatusLiveData.postValue(false)
            cheatChangesCommittedLiveEvent.postValue(Unit)
        }.subscribe({
            liveData.postValue(true)
        }, {
            liveData.postValue(false)
        }).addTo(disposables)

        return liveData
    }

    override fun onCleared() {
        super.onCleared()
        disposables.dispose()
    }
}