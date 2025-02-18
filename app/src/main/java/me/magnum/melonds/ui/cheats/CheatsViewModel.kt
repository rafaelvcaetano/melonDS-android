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
import me.magnum.melonds.parcelables.RomInfoParcelable
import me.magnum.melonds.parcelables.cheat.CheatFolderParcelable
import me.magnum.melonds.parcelables.cheat.CheatParcelable
import me.magnum.melonds.parcelables.cheat.GameParcelable
import me.magnum.melonds.ui.cheats.model.CheatsScreenUiState
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

    private val modifiedCheatSet = MutableStateFlow(savedStateHandle.get<List<CheatParcelable>>(KEY_MODIFIED_CHEATS).orEmpty().map { it.toCheat() })

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
                    val romInfo = savedStateHandle.get<RomInfoParcelable>(CheatsActivity.KEY_ROM_INFO)
                    if (romInfo == null) {
                        // Should never happen
                        emit(CheatsScreenUiState.Ready(emptyList()))
                    } else {
                        emit(CheatsScreenUiState.Loading())
                        val game = cheatsRepository.findGameForRom(romInfo.toRomInfo())
                        // This will reset the flow and will load the folders for this game
                        savedStateHandle[KEY_SELECTED_GAME] = game?.let { GameParcelable.fromGame(it) }
                    }
                } else {
                    emit(CheatsScreenUiState.Loading())
                    val folders = cheatsRepository.getAllGameCheats(it)
                    emit(CheatsScreenUiState.Ready(folders))
                }
            }
        }.shareIn(viewModelScope, started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 1000L), replay = 1)
    }

    val folderCheats by lazy {
        selectedCheatFolder.filterNotNull().flatMapLatest { cheatFolder ->
            modifiedCheatSet.map { modifiedCheats ->
                val cheats = cheatFolder.cheats.toMutableList()
                modifiedCheats.forEach { cheat ->
                    val originalCheatIndex = cheats.indexOfFirst { it.id == cheat.id }
                    if (originalCheatIndex >= 0) {
                        cheats[originalCheatIndex] = cheat
                    }
                }

                CheatsScreenUiState.Ready(cheats.toList())
            }
        }.shareIn(viewModelScope, started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 1000L), replay = 1)
    }

    val selectedGameCheats: SharedFlow<CheatsScreenUiState<List<CheatInFolder>>> by lazy {
        selectedGame.filterNotNull().flatMapLatest {
            val allGameCheats = cheatsRepository.getAllGameCheats(it)
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