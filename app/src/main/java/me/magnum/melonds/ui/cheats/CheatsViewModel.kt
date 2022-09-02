package me.magnum.melonds.ui.cheats

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.disposables.CompositeDisposable
import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.domain.model.CheatFolder
import me.magnum.melonds.domain.model.CheatInFolder
import me.magnum.melonds.domain.model.Game
import me.magnum.melonds.domain.repositories.CheatsRepository
import me.magnum.melonds.extensions.addTo
import me.magnum.melonds.parcelables.RomInfoParcelable
import me.magnum.melonds.utils.SingleLiveEvent
import javax.inject.Inject

@HiltViewModel
class CheatsViewModel @Inject constructor(
    private val cheatsRepository: CheatsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val selectedGame = MutableLiveData<Game>()
    private val selectedFolder = MutableLiveData<CheatFolder?>()
    private var allRomCheatsLiveData = MutableLiveData<List<Game>>()
    private val committingCheatsChangesStatusLiveData = MutableLiveData(false)
    private val cheatChangesCommittedLiveEvent = SingleLiveEvent<Unit>()
    private val modifiedCheatSet = mutableListOf<Cheat>()

    private val _openFoldersEvent = SingleLiveEvent<Unit>()
    val openFoldersEvent: LiveData<Unit> = _openFoldersEvent

    private val _openCheatsEvent = SingleLiveEvent<Unit>()
    val openCheatsEvent: LiveData<Unit> = _openCheatsEvent

    private val _openEnabledCheatsEvent = SingleLiveEvent<Unit>()
    val openEnabledCheatsEvent: LiveData<Unit> = _openEnabledCheatsEvent

    private val disposables = CompositeDisposable()

    init {
        val romInfo = savedStateHandle.get<RomInfoParcelable>(CheatsActivity.KEY_ROM_INFO) ?: error("No ROM info provided")

        cheatsRepository.getAllRomCheats(romInfo.toRomInfo()).subscribe {
            allRomCheatsLiveData.postValue(it)
            if (it.size == 1) {
                selectedGame.postValue(it.first())
            }
        }.addTo(disposables)
    }

    fun getRomCheats(): LiveData<List<Game>> {
        return allRomCheatsLiveData
    }

    fun getGames(): List<Game> {
        return allRomCheatsLiveData.value ?: emptyList()
    }

    fun getSelectedGame(): LiveData<Game> {
        return selectedGame
    }

    fun setSelectedGame(game: Game) {
        selectedGame.value = game
        _openFoldersEvent.postValue(Unit)
    }

    fun getSelectedFolder(): LiveData<CheatFolder?> {
        return selectedFolder
    }

    fun setSelectedFolder(folder: CheatFolder?) {
        selectedFolder.value = folder
        _openCheatsEvent.postValue(Unit)
    }

    fun getSelectedFolderCheats(): List<Cheat> {
        val cheats = selectedFolder.value?.cheats?.toMutableList() ?: mutableListOf()

        modifiedCheatSet.forEach { cheat ->
            val originalCheatIndex = cheats.indexOfFirst { it.id == cheat.id }
            if (originalCheatIndex >= 0) {
                cheats[originalCheatIndex] = cheat
            }
        }
        return cheats
    }

    fun getGameSelectedCheats(): List<CheatInFolder> {
        val cheats = selectedGame.value?.cheats?.flatMap { folder ->
            folder.cheats.map { CheatInFolder(it, folder.name) }
        }?.toMutableList() ?: mutableListOf()

        modifiedCheatSet.forEach { cheat ->
            val originalCheatIndex = cheats.indexOfFirst { it.cheat.id == cheat.id }
            if (originalCheatIndex >= 0) {
                cheats[originalCheatIndex] = CheatInFolder(cheat, cheats[originalCheatIndex].folderName)
            }
        }
        return cheats.filter { it.cheat.enabled }
    }

    fun notifyCheatEnabledStatusChanged(cheat: Cheat, isEnabled: Boolean) {
        // Compare new status with the original one and take action accordingly
        if (isEnabled == cheat.enabled) {
            modifiedCheatSet.removeAll { it.id == cheat.id }
        } else {
            modifiedCheatSet.add(cheat.copy(enabled = isEnabled))
        }
    }

    fun openEnabledCheats() {
        _openEnabledCheatsEvent.postValue(Unit)
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