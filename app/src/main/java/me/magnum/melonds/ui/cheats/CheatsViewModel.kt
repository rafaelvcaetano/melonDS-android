package me.magnum.melonds.ui.cheats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import me.magnum.melonds.common.suspendRunCatching
import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.domain.model.CheatFolder
import me.magnum.melonds.domain.model.CheatInFolder
import me.magnum.melonds.domain.model.Game
import me.magnum.melonds.domain.repositories.CheatsRepository
import me.magnum.melonds.parcelables.RomInfoParcelable
import me.magnum.melonds.ui.cheats.model.CheatsScreenUiState
import javax.inject.Inject

@HiltViewModel
class CheatsViewModel @Inject constructor(
    private val cheatsRepository: CheatsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val modifiedCheatSet = mutableListOf<Cheat>()

    private val _initialContentReady = MutableStateFlow(false)
    val initialContentReady = _initialContentReady.asStateFlow()

    private val _games = MutableStateFlow<CheatsScreenUiState<List<Game>>>(CheatsScreenUiState.Loading())
    val games = _games.asStateFlow()

    private val _selectedGame = MutableStateFlow<Game?>(null)
    val selectedGame = _selectedGame.asStateFlow()

    private val _selectedGameCheats = MutableStateFlow<CheatsScreenUiState<List<CheatFolder>>>(CheatsScreenUiState.Loading())
    val selectedGameCheats: StateFlow<CheatsScreenUiState<List<CheatFolder>>> get() {
        _selectedGameCheats.tryEmit(CheatsScreenUiState.Loading())
        loadCheatsForSelectedGame()
        return _selectedGameCheats.asStateFlow()
    }

    private val _selectedCheatFolder = MutableStateFlow<CheatFolder?>(null)
    val selectedCheatFolder = _selectedCheatFolder.asStateFlow()

    private val _openFoldersEvent = Channel<Unit>(Channel.CONFLATED)
    val openFoldersEvent = _openFoldersEvent.receiveAsFlow()

    private val _openCheatsEvent = Channel<Unit>(Channel.CONFLATED)
    val openCheatsEvent = _openCheatsEvent.receiveAsFlow()

    private val _openEnabledCheatsEvent = Channel<Unit>(Channel.CONFLATED)
    val openEnabledCheatsEvent = _openEnabledCheatsEvent.receiveAsFlow()

    private val _committingCheatsChangesState = MutableStateFlow(false)
    val committingCheatsChangesState = _committingCheatsChangesState.asStateFlow()

    private val _cheatChangesCommittedEvent = Channel<Boolean>(Channel.CONFLATED)
    val cheatChangesCommittedEvent = _cheatChangesCommittedEvent.receiveAsFlow()

    init {
        val romInfo = savedStateHandle.get<RomInfoParcelable>(CheatsActivity.KEY_ROM_INFO)

        viewModelScope.launch {
            if (romInfo != null) {
                val games = cheatsRepository.findGamesForRom(romInfo.toRomInfo())
                _games.emit(CheatsScreenUiState.Ready(games))
                if (games.size == 1) {
                    _selectedGame.emit(games.first())
                }
                _initialContentReady.emit(true)
            } else {
                cheatsRepository.observeGames().collectLatest {
                    _games.emit(CheatsScreenUiState.Ready(it))
                    _initialContentReady.emit(true)
                }

                /*cheatsRepository.getAllRomCheats(romInfo.toRomInfo()).subscribe {
                    allRomCheatsLiveData.postValue(it)
                    if (it.size == 1) {
                        _selectedGame.tryEmit(it.first())
                    }
                    _initialContentReady.tryEmit(true)
                }.addTo(disposables)*/
            }
        }
    }

    private fun loadCheatsForSelectedGame() {
        val selectedGame = _selectedGame.value ?: return

        viewModelScope.launch {
            val cheatFolders = cheatsRepository.getAllGameCheats(selectedGame)
            _selectedGameCheats.emit(CheatsScreenUiState.Ready(cheatFolders))
        }
    }

    fun setSelectedGame(game: Game) {
        _selectedGame.tryEmit(game)
        _openFoldersEvent.trySend(Unit)
    }

    fun setSelectedFolder(folder: CheatFolder) {
        _selectedCheatFolder.value = folder
        _openCheatsEvent.trySend(Unit)
    }

    fun getSelectedFolderCheats(): List<Cheat> {
        val cheats = _selectedCheatFolder.value?.cheats?.toMutableList() ?: mutableListOf()

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
        _openEnabledCheatsEvent.trySend(Unit)
    }

    fun commitCheatChanges() {
        if (modifiedCheatSet.isEmpty()) {
            _cheatChangesCommittedEvent.trySend(true)
            return
        }

        _committingCheatsChangesState.value = true
        viewModelScope.launch {
            suspendRunCatching {
                cheatsRepository.updateCheatsStatus(modifiedCheatSet)
            }.fold(
                onSuccess = { _cheatChangesCommittedEvent.trySend(true) },
                onFailure = { _cheatChangesCommittedEvent.trySend(false) },
            )
            _committingCheatsChangesState.value = false
        }
    }
}