package me.magnum.melonds.ui.dsiwaremanager.ui

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import me.magnum.melonds.R
import me.magnum.melonds.common.Permission
import me.magnum.melonds.common.contracts.FilePickerContract
import me.magnum.melonds.domain.model.DSiWareTitle
import me.magnum.melonds.domain.model.RomIconFiltering
import me.magnum.melonds.ui.dsiwaremanager.model.DSiWareManagerUiState
import me.magnum.melonds.ui.romlist.RomIcon
import me.magnum.melonds.ui.theme.MelonTheme

@Composable
fun DSiWareManager(
    modifier: Modifier,
    state: DSiWareManagerUiState,
    onImportTitle: (Uri) -> Unit,
    onDeleteTitle: (DSiWareTitle) -> Unit,
    retrieveTitleIcon: (DSiWareTitle) -> RomIcon,
) {
    when (state) {
        is DSiWareManagerUiState.Loading -> Loading(modifier)
        is DSiWareManagerUiState.Ready -> {
            Ready(
                modifier = modifier,
                titles = state.titles,
                onImportTitle = onImportTitle,
                onDeleteTitle = onDeleteTitle,
                retrieveTitleIcon = retrieveTitleIcon,
            )
        }
    }
}

@Composable
private fun Loading(modifier: Modifier) {
    Box(modifier) {
        CircularProgressIndicator(
            modifier = Modifier.align(Alignment.Center),
            color = MaterialTheme.colors.onSurface,
        )
    }
}

@Composable
private fun Ready(
    modifier: Modifier,
    titles: List<DSiWareTitle>,
    onImportTitle: (Uri) -> Unit,
    onDeleteTitle: (DSiWareTitle) -> Unit,
    retrieveTitleIcon: (DSiWareTitle) -> RomIcon,
) {
    val openRomLauncher = rememberLauncherForActivityResult(FilePickerContract(Permission.READ)) {
        if (it != null) {
            onImportTitle(it)
        }
    }

    Box(modifier = modifier) {
        if (titles.isEmpty()) {
            Text(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                text = stringResource(id = R.string.no_dsiware_titles_installed),
            )
        } else {
            DSiWareTitleList(
                modifier = Modifier.fillMaxSize(),
                titles = titles,
                onDeleteTitle = onDeleteTitle,
                retrieveTitleIcon = retrieveTitleIcon,
            )
        }

        FloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
            onClick = { openRomLauncher.launch(null to arrayOf("*/*")) }
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_add),
                contentDescription = stringResource(id = R.string.import_dsiware_title),
                tint = MaterialTheme.colors.onSecondary,
            )
        }
    }
}

@Composable
private fun DSiWareTitleList(modifier: Modifier, titles: List<DSiWareTitle>, onDeleteTitle: (DSiWareTitle) -> Unit, retrieveTitleIcon: (DSiWareTitle) -> RomIcon) {
    LazyColumn(modifier) {
        items(
            items = titles,
            key = { it.titleId },
        ) {
            DSiWareItem(
                modifier = Modifier.fillMaxWidth(),
                item = it,
                onDeleteClicked = { onDeleteTitle(it) },
                retrieveTitleIcon = { retrieveTitleIcon(it) },
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewDSiWareManagerReady() {
    val bitmap = createBitmap(1, 1).apply { this[0, 0] = 0xFF777777.toInt() }

    MelonTheme {
        DSiWareManager(
            modifier = Modifier.fillMaxSize(),
            state = DSiWareManagerUiState.Ready(
                listOf(
                    DSiWareTitle("Legit Game", "Notendo", 0, ByteArray(0)),
                    DSiWareTitle("Legit Game: Snapped!", "Upasuft", 0, ByteArray(0)),
                    DSiWareTitle("Highway 4 - Mediocre Racing", "Microware", 0, ByteArray(0)),
                )
            ),
            onImportTitle = {},
            onDeleteTitle = {},
            retrieveTitleIcon = { RomIcon(bitmap, RomIconFiltering.NONE) },
        )
    }
}