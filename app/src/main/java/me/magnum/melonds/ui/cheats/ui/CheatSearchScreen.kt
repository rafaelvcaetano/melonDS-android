package me.magnum.melonds.ui.cheats.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.domain.model.CheatInFolder
import me.magnum.melonds.domain.model.CheatFolder
import me.magnum.melonds.ui.cheats.model.CheatFormDialogState
import me.magnum.melonds.ui.cheats.model.CheatSubmissionForm
import me.magnum.melonds.ui.cheats.model.CheatsScreenUiState
import me.magnum.melonds.ui.cheats.ui.cheatform.CheatFormDialog
import me.magnum.melonds.ui.cheats.ui.item.CheatInFolderItem

@Composable
fun CheatSearchScreen(
    modifier: Modifier,
    contentPadding: PaddingValues,
    folders: CheatsScreenUiState<List<CheatFolder>>,
    query: String,
    folderName: String?,
    onCheatClick: (Cheat) -> Unit,
    onUpdateCheat: (Cheat, CheatSubmissionForm) -> Unit,
    onDeleteCheatClick: (CheatFolder, Cheat) -> Unit,
) {
    var cheatFormDialogState by rememberSaveable(stateSaver = CheatFormDialogState.Saver) {
        mutableStateOf(CheatFormDialogState.Hidden)
    }

    when (folders) {
        is CheatsScreenUiState.Loading -> LoadingScreen(modifier.padding(contentPadding))
        is CheatsScreenUiState.Ready -> {
            val searchResults = remember(folders.data, query, folderName) {
                folders.data.asSequence()
                    .filter { folderName == null || it.name == folderName }
                    .flatMap { folder ->
                        val folderMatches = query.isNotBlank() && folder.name.contains(query, ignoreCase = true)
                        folder.cheats.asSequence()
                            .filter { query.isBlank() || folderMatches || it.name.contains(query, ignoreCase = true) }
                            .map { cheat -> folder to cheat }
                    }
                    .toList()
            }

            if (searchResults.isEmpty()) {
                Box(modifier.fillMaxSize().padding(contentPadding)) {
                    Text(
                        modifier = Modifier.padding(24.dp).align(Alignment.Center),
                        text = stringResource(R.string.no_cheats_found),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                        top = contentPadding.calculateTopPadding(),
                        end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
                        bottom = contentPadding.calculateBottomPadding() + 16.dp,
                    ),
                ) {
                    items(searchResults) { (folder, cheat) ->
                        CheatInFolderItem(
                            modifier = Modifier.fillMaxWidth(),
                            cheatInFolder = CheatInFolder(cheat, folder.name),
                            highlightQuery = query,
                            onEditClick = { cheatFormDialogState = CheatFormDialogState.EditCheat(cheat) },
                            onDeleteClick = { onDeleteCheatClick(folder, cheat) },
                            onClick = { onCheatClick(cheat) },
                        )
                    }
                }
            }
        }
    }

    CheatFormDialog(
        state = cheatFormDialogState,
        onDismiss = { cheatFormDialogState = CheatFormDialogState.Hidden },
        onSaveCheat = { form ->
            val dialogState = cheatFormDialogState
            if (dialogState is CheatFormDialogState.EditCheat) {
                onUpdateCheat(dialogState.cheat, form)
            }
            cheatFormDialogState = CheatFormDialogState.Hidden
        },
    )
}
