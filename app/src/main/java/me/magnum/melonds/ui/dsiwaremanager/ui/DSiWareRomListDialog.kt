package me.magnum.melonds.ui.dsiwaremanager.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFromBaseline
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalElevationOverlay
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.lifecycle.viewmodel.compose.viewModel
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.Rom
import me.magnum.melonds.domain.model.RomConfig
import me.magnum.melonds.domain.model.RomIconFiltering
import me.magnum.melonds.ui.common.FullScreen
import me.magnum.melonds.ui.dsiwaremanager.DSiWareRomListViewModel
import me.magnum.melonds.ui.dsiwaremanager.model.DSiWareMangerRomListUiState
import me.magnum.melonds.ui.romlist.RomIcon
import me.magnum.melonds.ui.theme.MelonTheme
import me.magnum.melonds.ui.theme.toolbarBackground

@Composable
fun DSiWareRomListDialog(
    romListViewModel: DSiWareRomListViewModel = viewModel(),
    onDismiss: () -> Unit,
    onRomSelected: (Rom) -> Unit,
) {
    val romsUiState by romListViewModel.dsiWareRoms.collectAsState()

    DSiWareRomListDialogImpl(
        romsUiState = romsUiState,
        onDismiss = onDismiss,
        onRomSelected = onRomSelected,
        retrieveRomIcon = { romListViewModel.getRomIcon(it) },
    )
}

@Composable
private fun DSiWareRomListDialogImpl(
    romsUiState: DSiWareMangerRomListUiState,
    onDismiss: () -> Unit,
    onRomSelected: (Rom) -> Unit,
    retrieveRomIcon: suspend (Rom) -> RomIcon,
) {
    val isLargeScreen = LocalConfiguration.current.screenWidthDp > 840
    if (isLargeScreen) {
        PopupDialog(
            romsUiState = romsUiState,
            onDismiss = onDismiss,
            onRomSelected = onRomSelected,
            retrieveRomIcon = retrieveRomIcon,
        )
    } else {
        FullScreenDialog(
            romsUiState = romsUiState,
            onDismiss = onDismiss,
            onRomSelected = onRomSelected,
            retrieveRomIcon = retrieveRomIcon,
        )
    }
}

@Composable
private fun FullScreenDialog(
    romsUiState: DSiWareMangerRomListUiState,
    onDismiss: () -> Unit,
    onRomSelected: (Rom) -> Unit,
    retrieveRomIcon: suspend (Rom) -> RomIcon
) {
    FullScreen(onDismiss = onDismiss) {
        Surface {
            Column(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalElevationOverlay provides null) {
                    TopAppBar(
                        modifier = Modifier.fillMaxWidth(),
                        title = {
                            Text(
                                text = stringResource(id = R.string.select_dsiware_title),
                                color = MaterialTheme.colors.onPrimary,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(id = R.string.close),
                                    tint = MaterialTheme.colors.onPrimary,
                                )
                            }
                        },
                        backgroundColor = MaterialTheme.colors.toolbarBackground,
                    )
                }

                when (romsUiState) {
                    is DSiWareMangerRomListUiState.Loading -> {
                        Loading(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                    is DSiWareMangerRomListUiState.Loaded -> {
                        DSiWareRomList(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            roms = romsUiState.roms,
                            onRomSelected = onRomSelected,
                            retrieveRomIcon = retrieveRomIcon,
                        )
                    }
                    is DSiWareMangerRomListUiState.Empty -> {
                        Empty(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PopupDialog(
    romsUiState: DSiWareMangerRomListUiState,
    onDismiss: () -> Unit,
    onRomSelected: (Rom) -> Unit,
    retrieveRomIcon: suspend (Rom) -> RomIcon
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(2.dp)) {
            Column {
                Text(
                    modifier = Modifier
                        .paddingFromBaseline(top = 40.dp, bottom = 24.dp)
                        .padding(start = 24.dp),
                    text = stringResource(id = R.string.select_dsiware_title),
                    style = MaterialTheme.typography.h6,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                when (romsUiState) {
                    is DSiWareMangerRomListUiState.Loading -> {
                        Loading(Modifier.fillMaxWidth())
                    }
                    is DSiWareMangerRomListUiState.Loaded -> {
                        DSiWareRomList(
                            Modifier,
                            roms = romsUiState.roms,
                            onRomSelected = onRomSelected,
                            retrieveRomIcon = retrieveRomIcon,
                            romItemContentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                        )
                    }
                    is DSiWareMangerRomListUiState.Empty -> {
                        Empty(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun Loading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun DSiWareRomList(
    modifier: Modifier,
    roms: List<Rom>,
    onRomSelected: (Rom) -> Unit,
    retrieveRomIcon: suspend (Rom) -> RomIcon,
    romItemContentPadding: PaddingValues = DefaultRomItemPadding,
) {
    val state = rememberLazyListState()
    val canBeScrolled by remember {
        derivedStateOf {
            val layoutInfo = state.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            if (layoutInfo.totalItemsCount == 0) {
                false
            } else {
                val firstVisibleItem = visibleItemsInfo.first()
                val lastVisibleItem = visibleItemsInfo.last()

                val viewportHeight = layoutInfo.viewportEndOffset + layoutInfo.viewportStartOffset

                !(firstVisibleItem.index == 0 &&
                        firstVisibleItem.offset == 0 &&
                        lastVisibleItem.index + 1 == layoutInfo.totalItemsCount &&
                        lastVisibleItem.offset + lastVisibleItem.size <= viewportHeight)
            }
        }
    }

    Column(modifier) {
        if (canBeScrolled) {
            Divider()
        }

        LazyColumn(state = state) {
            items(roms) {
                RomItem(
                    modifier = Modifier.fillMaxWidth(),
                    item = it,
                    onClick = { onRomSelected(it) },
                    retrieveTitleIcon = { retrieveRomIcon(it) },
                    contentPadding = romItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun Empty(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(16.dp), contentAlignment = Alignment.Center) {
        Text(text = stringResource(id = R.string.no_dsiware_roms_found))
    }
}

@Composable
@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Preview(device = Devices.TABLET)
@Preview(device = Devices.TABLET, uiMode = UI_MODE_NIGHT_YES)
private fun PreviewDSiWareRomListDialog() {
    val bitmap = createBitmap(1, 1).apply { this[0, 0] = 0xFF777777.toInt() }

    MelonTheme {
        DSiWareRomListDialogImpl(
            romsUiState = DSiWareMangerRomListUiState.Loaded(
                listOf(
                    Rom("Legit Game", "Gamewicked", "legit_game.nds", Uri.EMPTY, Uri.EMPTY, RomConfig(), null, true, ""),
                    Rom("Legit Game: Snapped!", "Nontendo", "legit_game_snapped.nds", Uri.EMPTY, Uri.EMPTY, RomConfig(), null, true, ""),
                    Rom("Highway 4 - Mediocre Racing", "Someware", "highway_4_mediocre_racing.nds", Uri.EMPTY, Uri.EMPTY, RomConfig(), null, true, ""),
                )
            ),
            onDismiss = {},
            onRomSelected = {},
            retrieveRomIcon = { RomIcon(bitmap, RomIconFiltering.NONE) },
        )
    }
}

@Composable
@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Preview(device = Devices.TABLET)
@Preview(device = Devices.TABLET, uiMode = UI_MODE_NIGHT_YES)
private fun PreviewDSiWareRomListDialogEmpty() {
    MelonTheme {
        DSiWareRomListDialogImpl(
            romsUiState = DSiWareMangerRomListUiState.Empty,
            onDismiss = {},
            onRomSelected = {},
            retrieveRomIcon = { RomIcon(null, RomIconFiltering.NONE) },
        )
    }
}