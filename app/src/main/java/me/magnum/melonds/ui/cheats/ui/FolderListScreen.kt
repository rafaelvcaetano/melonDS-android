package me.magnum.melonds.ui.cheats.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.CheatFolder
import me.magnum.melonds.ui.cheats.model.CheatsScreenUiState
import me.magnum.melonds.ui.cheats.ui.item.FolderItem
import me.magnum.melonds.ui.common.component.dialog.TextInputDialog
import me.magnum.melonds.ui.common.component.dialog.rememberTextInputDialogState

@Composable
fun FolderListScreen(
    modifier: Modifier,
    contentPadding: PaddingValues,
    folders: CheatsScreenUiState<List<CheatFolder>>,
    onFolderClick: (CheatFolder) -> Unit,
    onAddFolder: (String) -> Unit,
) {
    when (folders) {
        is CheatsScreenUiState.Loading -> LoadingScreen(modifier.padding(contentPadding))
        is CheatsScreenUiState.Ready -> List(
            modifier = modifier,
            contentPadding = contentPadding,
            folders = folders.data,
            onFolderClick = onFolderClick,
            onAddFolder = onAddFolder,
        )
    }
}

@Composable
private fun List(
    modifier: Modifier,
    contentPadding: PaddingValues,
    folders: List<CheatFolder>,
    onFolderClick: (CheatFolder) -> Unit,
    onAddFolder: (String) -> Unit,
) {
    val newFolderDialogState = rememberTextInputDialogState()
    val resources = LocalContext.current.resources

    Box(modifier) {
        if (folders.isEmpty()) {
            Text(
                modifier = Modifier.padding(contentPadding).padding(24.dp).align(Alignment.Center),
                text = stringResource(R.string.no_cheats_found),
                textAlign = TextAlign.Center,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().consumeWindowInsets(contentPadding),
                contentPadding = PaddingValues(
                    start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                    top = contentPadding.calculateTopPadding(),
                    end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
                    bottom = contentPadding.calculateBottomPadding() + 16.dp + 56.dp + 16.dp, // Take FAB into consideration
                ),
            ) {
                items(folders) {
                    FolderItem(
                        modifier = Modifier.fillMaxWidth(),
                        folder = it,
                        onClick = { onFolderClick(it) },
                    )
                }
            }
        }

        FloatingActionButton(
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                    end = contentPadding.calculateEndPadding(LocalLayoutDirection.current) + 16.dp,
                ),
            onClick = {
                newFolderDialogState.show(
                    initialText = resources.getString(R.string.cheat_folder_default_name),
                    onConfirm = {
                        onAddFolder(it)
                    }
                )
            },
        ) {
            Icon(
                painter = rememberVectorPainter(Icons.Filled.CreateNewFolder),
                contentDescription = stringResource(R.string.add_cheat_folder),
            )
        }
    }

    TextInputDialog(
        title = stringResource(R.string.add_cheat_folder),
        dialogState = newFolderDialogState,
    )
}