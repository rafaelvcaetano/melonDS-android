package me.magnum.melonds.ui.cheats.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.magnum.melonds.domain.model.Game
import me.magnum.melonds.ui.cheats.model.CheatsScreenUiState
import me.magnum.melonds.ui.cheats.ui.item.GameItem

@Composable
fun GameListScreen(
    modifier: Modifier,
    contentPadding: PaddingValues,
    games: CheatsScreenUiState<List<Game>>,
    onGameClick: (Game) -> Unit,
) {
    when (games) {
        is CheatsScreenUiState.Loading -> LoadingScreen(modifier.padding(contentPadding))
        is CheatsScreenUiState.Ready -> List(
            modifier = modifier,
            contentPadding = contentPadding,
            games = games.data,
            onGameClick = onGameClick,
        )
    }
}

@Composable
private fun List(
    modifier: Modifier,
    contentPadding: PaddingValues,
    games: List<Game>,
    onGameClick: (Game) -> Unit,
) {
    LazyColumn(
        modifier = modifier.consumeWindowInsets(contentPadding),
        contentPadding = contentPadding,
    ) {
        items(games) {
            GameItem(
                modifier = Modifier.fillMaxWidth(),
                game = it,
                onClick = { onGameClick(it) },
            )
        }
    }
}