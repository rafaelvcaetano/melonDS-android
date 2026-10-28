package me.magnum.melonds.ui.cheats.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.testTag
import me.magnum.melonds.ui.cheats.model.CheatSearchIndex
import me.magnum.melonds.ui.cheats.model.CheatSearchRow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.CheatFolder
import me.magnum.melonds.ui.cheats.model.CheatsScreenUiState
import me.magnum.melonds.ui.cheats.ui.item.FolderItem
import me.magnum.melonds.ui.cheats.ui.item.HighlightedText
import me.magnum.melonds.ui.common.component.dialog.TextInputDialog
import me.magnum.melonds.ui.common.component.dialog.rememberTextInputDialogState
import me.magnum.melonds.ui.common.melonOutlinedTextFieldColors

@Composable
fun FolderListScreen(
    modifier: Modifier,
    contentPadding: PaddingValues,
    folders: CheatsScreenUiState<List<CheatFolder>>,
    onFolderClick: (CheatFolder) -> Unit,
    onAddFolder: (String) -> Unit,
    onSearchResultClick: (CheatSearchRow.Match) -> Unit = { onFolderClick(it.folder) },
) {
    // Keep the query when the repository emits Loading while this destination is restored.
    var searchQuery by rememberSaveable { mutableStateOf("") }
    when (folders) {
        is CheatsScreenUiState.Loading -> LoadingScreen(modifier.padding(contentPadding))
        is CheatsScreenUiState.Ready -> List(
            modifier = modifier,
            contentPadding = contentPadding,
            folders = folders.data,
            onFolderClick = onFolderClick,
            onAddFolder = onAddFolder,
            onSearchResultClick = onSearchResultClick,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
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
    onSearchResultClick: (CheatSearchRow.Match) -> Unit = { onFolderClick(it.folder) },
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
) {
    val newFolderDialogState = rememberTextInputDialogState()
    val resources = LocalResources.current
    val index by produceState<CheatSearchIndex?>(null, folders) {
        value = null
        value = withContext(Dispatchers.Default) { CheatSearchIndex.build(folders) }
    }
    val query = searchQuery.trim()
    val searchResults = remember(folders, index, query) {
        if (query.isEmpty()) folders.map { CheatSearchRow.Folder(it) }
        else index?.takeIf { it.folders == folders }?.search(query)
    }

    Box(modifier) {
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(
                    top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding(),
                )
                .consumeWindowInsets(contentPadding),
        ) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                singleLine = true,
                label = { Text(stringResource(R.string.hint_search_cheats_and_folders)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.clear),
                            )
                        }
                    }
                },
                colors = melonOutlinedTextFieldColors(),
            )

            if (searchResults == null) {
                LoadingScreen(Modifier.weight(1f).fillMaxWidth())
            } else if (searchResults.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Text(
                        modifier = Modifier.padding(24.dp).align(Alignment.Center),
                        text = stringResource(if (query.isEmpty()) R.string.no_cheats_found else R.string.cheat_search_no_results),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("cheat-search-results"),
                    contentPadding = PaddingValues(
                        start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                        end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
                        bottom = 16.dp + 56.dp + 16.dp, // Take FAB into consideration
                    ),
                ) {
                    items(searchResults, key = { it.key }, contentType = { it is CheatSearchRow.Folder }) { result ->
                        when (result) {
                            is CheatSearchRow.Folder -> FolderItem(
                                modifier = Modifier.fillMaxWidth(),
                                folder = result.folder,
                                highlightQuery = query,
                                onClick = { onFolderClick(result.folder) },
                            )
                            is CheatSearchRow.Match -> Column(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .clickable { onSearchResultClick(result) }
                                    .padding(start = 72.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                HighlightedText(text = result.label, query = query, style = MaterialTheme.typography.body2)
                                result.familyTitle?.let { title ->
                                    Text(text = title, style = MaterialTheme.typography.caption)
                                }
                            }
                        }
                    }
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
