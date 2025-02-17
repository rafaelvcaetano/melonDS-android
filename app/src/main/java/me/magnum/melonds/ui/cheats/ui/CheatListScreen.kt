package me.magnum.melonds.ui.cheats.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.ui.cheats.model.CheatsScreenUiState
import me.magnum.melonds.ui.cheats.ui.item.CheatItem

@Composable
fun CheatListScreen(
    modifier: Modifier,
    contentPadding: PaddingValues,
    cheats: CheatsScreenUiState<List<Cheat>>,
    onCheatClick: (Cheat) -> Unit,
) {
    when (cheats) {
        is CheatsScreenUiState.Loading -> LoadingScreen(modifier.padding(contentPadding))
        is CheatsScreenUiState.Ready -> List(
            modifier = modifier,
            contentPadding = contentPadding,
            cheats = cheats.data,
            onCheatClick = onCheatClick,
        )
    }
}

@Composable
private fun List(
    modifier: Modifier,
    contentPadding: PaddingValues,
    cheats: List<Cheat>,
    onCheatClick: (Cheat) -> Unit,
) {
    LazyColumn(
        modifier = modifier.consumeWindowInsets(contentPadding),
        contentPadding = contentPadding,
    ) {
        itemsIndexed(
            items = cheats,
            key = { _, item -> item.id ?: item.code },
        ) { index, item ->
            if (index > 0) {
                Divider()
            }

            CheatItem(
                modifier = Modifier.fillMaxWidth(),
                cheat = item,
                onClick = { onCheatClick(item) },
            )
        }
    }
}