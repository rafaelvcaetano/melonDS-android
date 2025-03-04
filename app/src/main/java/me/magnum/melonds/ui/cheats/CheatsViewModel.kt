package me.magnum.melonds.ui.cheats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.magnum.melonds.common.suspendRunCatching
import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.domain.model.CheatFolder
import me.magnum.melonds.domain.model.CheatInFolder
import me.magnum.melonds.domain.model.Game
import me.magnum.melonds.domain.repositories.CheatsRepository
import me.magnum.melonds.extensions.removeFirst
import me.magnum.melonds.parcelables.RomInfoParcelable
import me.magnum.melonds.parcelables.cheat.CheatFolderParcelable
import me.magnum.melonds.parcelables.cheat.CheatParcelable
import me.magnum.melonds.parcelables.cheat.GameParcelable
import me.magnum.melonds.ui.cheats.model.CheatSubmissionForm
import me.magnum.melonds.ui.cheats.model.CheatsScreenUiState
import me.magnum.melonds.ui.cheats.model.DeletedCheat
import me.magnum.melonds.ui.cheats.model.OpenScreenEvent
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CheatsViewModel @Inject constructor(
    private val cheatsRepository: CheatsRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        const val KEY_MODIFIED_CHEATS = "modified_cheats"
        const val KEY_SELECTED_GAME = "selected_game"
        const val KEY_SELECTED_FOLDER = "selected_folder"
    }

    private val romInfo = savedStateHandle.get<RomInfoParcelable>(CheatsActivity.KEY_ROM_INFO)?.toRomInfo()
    private val modifiedCheatSet = MutableStateFlow(savedStateHandle.get<List<CheatParcelable>>(KEY_MODIFIED_CHEATS).orEmpty().map { it.toCheat() })
    private val deletedCheats = mutableListOf<DeletedCheat>()

    private val selectedGame = savedStateHandle.getStateFlow<GameParcelable?>(KEY_SELECTED_GAME, null).map { it?.toGame() }
    private val selectedCheatFolder = savedStateHandle.getStateFlow<CheatFolderParcelable?>(KEY_SELECTED_FOLDER, null).map { it?.toCheatFolder() }

    val games by lazy {
        flow {
            emit(CheatsScreenUiState.Loading())
            val games = cheatsRepository.getGames()
            emit(CheatsScreenUiState.Ready(games))
        }.shareIn(viewModelScope, started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 1000L), replay = 1)
    }

    val folders by lazy {
        selectedGame.flatMapLatest {
            flow {
                if (it == null) {
                    // No game is selected. Try to load it based on the ROM info
                    if (romInfo == null) {
                        // Should never happen
                        emit(CheatsScreenUiState.Ready(emptyList()))
                    } else {
                        emit(CheatsScreenUiState.Loading())
                        val game = cheatsRepository.findGameForRom(romInfo)
                        if (game != null) {
                            // This will reset the flow and will load the folders for this game
                            savedStateHandle[KEY_SELECTED_GAME] = GameParcelable.fromGame(game)
                        } else {
                            emit(CheatsScreenUiState.Ready(emptyList()))
                        }
                    }
                } else {
                    emit(CheatsScreenUiState.Loading())
                    cheatsRepository.getAllGameCheats(it)
                        .map { CheatsScreenUiState.Ready(it) }
                        .collect(this)
                }
            }
        }.shareIn(viewModelScope, started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 1000L), replay = 1)
    }

    val folderCheats by lazy {
        selectedCheatFolder.filterNotNull()
            .flatMapLatest { cheatsRepository.getFolderCheats(it) }
            .flatMapLatest { cheats ->
                modifiedCheatSet.map { modifiedCheats ->
                    val cheats = cheats.toMutableList()
                    modifiedCheats.forEach { cheat ->
                        val originalCheatIndex = cheats.indexOfFirst { it.id == cheat.id }
                        if (originalCheatIndex >= 0) {
                            cheats[originalCheatIndex] = cheat
                        }
                    }

                    CheatsScreenUiState.Ready(cheats as List<Cheat>)
                }
            }.shareIn(viewModelScope, started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 1000L), replay = 1)
    }

    val selectedGameCheats: SharedFlow<CheatsScreenUiState<List<CheatInFolder>>> by lazy {
        selectedGame.flatMapLatest {
            if (it == null) {
                flowOf(emptyList())
            } else {
                cheatsRepository.getAllGameCheats(it)
            }
        }.flatMapLatest { allGameCheats ->
            // Enabled cheats when the user entered the screen
            val gameCheats = allGameCheats.flatMap { folder ->
                folder.cheats.filter { it.enabled }.map { CheatInFolder(it, folder.name) }
            }.toMutableList()

            // Merge initially enabled cheats with latest changes
            modifiedCheatSet.value.forEach { cheat ->
                val originalCheatIndex = gameCheats.indexOfFirst { it.cheat.id == cheat.id }
                if (originalCheatIndex >= 0) {
                    if (cheat.enabled) {
                        gameCheats[originalCheatIndex] = CheatInFolder(cheat, gameCheats[originalCheatIndex].folderName)
                    } else {
                        gameCheats.removeAt(originalCheatIndex)
                    }
                } else if (cheat.enabled) {
                    val folder = allGameCheats.firstOrNull { it.cheats.any { it.id == cheat.id } }
                    if (folder != null) {
                        gameCheats.add(CheatInFolder(cheat, folder.name))
                    }
                }
            }

            // Update cheats as they are modified. Skip first event since we already have the up-to-date list at this point
            modifiedCheatSet.drop(1).map {
                it.forEach { cheat ->
                    val originalCheatIndex = gameCheats.indexOfFirst { it.cheat.id == cheat.id }
                    if (originalCheatIndex >= 0) {
                        gameCheats[originalCheatIndex] = CheatInFolder(cheat, gameCheats[originalCheatIndex].folderName)
                    }
                }
                CheatsScreenUiState.Ready(gameCheats.toList())
            }.onStart { emit(CheatsScreenUiState.Ready(gameCheats.toList())) }
        }.shareIn(viewModelScope, started = SharingStarted.WhileSubscribed(), replay = 1)
    }

    private val _openGamesEvent = Channel<OpenScreenEvent>(Channel.CONFLATED)
    val openGamesEvent = _openGamesEvent.receiveAsFlow()

    private val _openFoldersEvent = Channel<OpenScreenEvent>(Channel.CONFLATED)
    val openFoldersEvent = _openFoldersEvent.receiveAsFlow()

    private val _openCheatsEvent = Channel<OpenScreenEvent>(Channel.CONFLATED)
    val openCheatsEvent = _openCheatsEvent.receiveAsFlow()

    private val _openEnabledCheatsEvent = Channel<Unit>(Channel.CONFLATED)
    val openEnabledCheatsEvent = _openEnabledCheatsEvent.receiveAsFlow()

    private val _committingCheatsChangesState = MutableStateFlow(false)
    val committingCheatsChangesState = _committingCheatsChangesState.asStateFlow()

    private val _cheatChangesCommittedEvent = Channel<Boolean>(Channel.CONFLATED)
    val cheatChangesCommittedEvent = _cheatChangesCommittedEvent.receiveAsFlow()

    fun setSelectedGame(game: Game) {
        savedStateHandle[KEY_SELECTED_GAME] = GameParcelable.fromGame(game)
        _openFoldersEvent.trySend(OpenScreenEvent(game.name))
    }

    fun setSelectedFolder(folder: CheatFolder) {
        savedStateHandle[KEY_SELECTED_FOLDER] = CheatFolderParcelable.fromCheatFolder(folder)
        _openCheatsEvent.trySend(OpenScreenEvent(folder.name))
    }

    fun addFolder(folderName: String) {
        if (folderName.isBlank()) return

        val selectedGame = savedStateHandle.get<GameParcelable>(KEY_SELECTED_GAME)?.toGame()

        viewModelScope.launch {
            val folderGame = if (selectedGame != null) {
                selectedGame
            } else {
                if (romInfo == null) {
                    // Should never happen
                    return@launch
                } else {
                    // A game needs to be created to be associated with the folder
                    val newGame = Game(
                        id = null,
                        name = romInfo.gameName,
                        gameCode = romInfo.gameCode,
                        gameChecksum = romInfo.headerChecksumString(),
                        cheats = emptyList()
                    )
                    cheatsRepository.addGameCheats(newGame)
                }
            }

            cheatsRepository.addCheatFolder(folderName, folderGame)
            if (selectedGame == null) {
                // Update selected game with the new one
                savedStateHandle[KEY_SELECTED_GAME] = GameParcelable.fromGame(folderGame)
            }
        }
    }

    fun toggleCheat(cheat: Cheat) {
        if (committingCheatsChangesState.value) {
            // Already commiting changes. Cannot modify cheats now
            return
        }

        modifiedCheatSet.update {
            val cheatIndex = it.indexOfFirst { it.id == cheat.id }
            val updatedCheat = cheat.copy(enabled = !cheat.enabled)

            it.toMutableList().apply {
                if (cheatIndex >= 0) {
                    this[cheatIndex] = updatedCheat
                } else {
                    add(updatedCheat)
                }
            }.also {
                savedStateHandle[KEY_MODIFIED_CHEATS] = it.map { CheatParcelable.fromCheat(it) }
            }
        }
    }

    fun addNewCheat(cheatSubmissionForm: CheatSubmissionForm) {
        if (!cheatSubmissionForm.isValid()) return
        val selectedFolder = savedStateHandle.get<CheatFolderParcelable>(KEY_SELECTED_FOLDER) ?: return

        viewModelScope.launch {
            cheatsRepository.addCustomCheat(selectedFolder.toCheatFolder(), cheatSubmissionForm)
        }
    }

    fun updateCheat(originalCheat: Cheat, cheatSubmissionForm: CheatSubmissionForm) {
        if (!cheatSubmissionForm.isValid()) return
        if (originalCheat.name == cheatSubmissionForm.name && originalCheat.description == cheatSubmissionForm.description && originalCheat.code == cheatSubmissionForm.code) {
            // No changes were made. Do nothing
            return
        }

        val updatedCheat = originalCheat.copy(
            name = cheatSubmissionForm.name,
            description = cheatSubmissionForm.description.takeUnless { it.isBlank() },
            code = cheatSubmissionForm.code,
        )
        viewModelScope.launch {
            cheatsRepository.updateCheat(updatedCheat)
        }
    }

    fun deleteCheat(cheat: Cheat) {
        val selectedFolder = savedStateHandle.get<CheatFolderParcelable>(KEY_SELECTED_FOLDER) ?: return

        viewModelScope.launch {
            cheatsRepository.deleteCheat(cheat)
            deletedCheats.add(DeletedCheat(cheat, selectedFolder.toCheatFolder()))
        }
    }

    fun undoCheatDeletion(cheat: Cheat) {
        val deletedCheat = deletedCheats.removeFirst { it.cheat.id == cheat.id } ?: return
        viewModelScope.launch {
            cheatsRepository.addCheat(deletedCheat.folder, deletedCheat.cheat)
        }
    }

    fun openEnabledCheats() {
        _openEnabledCheatsEvent.trySend(Unit)
    }

    fun commitCheatChanges() {
        if (committingCheatsChangesState.value) {
            // Already commiting changes. Do nothing
            return
        }

        if (modifiedCheatSet.value.isEmpty()) {
            _cheatChangesCommittedEvent.trySend(true)
            return
        }

        _committingCheatsChangesState.value = true
        viewModelScope.launch {
            suspendRunCatching {
                cheatsRepository.updateCheatsStatus(modifiedCheatSet.value)
            }.fold(
                onSuccess = { _cheatChangesCommittedEvent.trySend(true) },
                onFailure = { _cheatChangesCommittedEvent.trySend(false) },
            )
            _committingCheatsChangesState.value = false
        }
    }
}