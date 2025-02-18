package me.magnum.melonds.ui.cheats.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.CheatFolder
import me.magnum.melonds.ui.cheats.model.CheatsScreenUiState
import me.magnum.melonds.ui.cheats.ui.item.FolderItem

@Composable
fun FolderListScreen(
    modifier: Modifier,
    contentPadding: PaddingValues,
    folders: CheatsScreenUiState<List<CheatFolder>>,
    onFolderClick: (CheatFolder) -> Unit,
) {
    when (folders) {
        is CheatsScreenUiState.Loading -> LoadingScreen(modifier.padding(contentPadding))
        is CheatsScreenUiState.Ready -> List(
            modifier = modifier,
            contentPadding = contentPadding,
            folders = folders.data,
            onFolderClick = onFolderClick,
        )
    }
}

@Composable
private fun List(
    modifier: Modifier,
    contentPadding: PaddingValues,
    folders: List<CheatFolder>,
    onFolderClick: (CheatFolder) -> Unit,
) {
    if (folders.isEmpty()) {
        Box(modifier = modifier.padding(contentPadding)) {
            Text(
                modifier = Modifier.padding(24.dp).align(Alignment.Center),
                text = stringResource(R.string.no_cheats_found),
                textAlign = TextAlign.Center,
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.consumeWindowInsets(contentPadding),
            contentPadding = contentPadding,
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
}