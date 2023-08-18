package me.magnum.melonds.ui.dsiwaremanager.ui

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import me.magnum.melonds.R
import me.magnum.melonds.common.Permission
import me.magnum.melonds.common.contracts.FilePickerContract
import me.magnum.melonds.domain.model.ConfigurationDirResult
import me.magnum.melonds.domain.model.DSiWareTitle
import me.magnum.melonds.domain.model.Rom
import me.magnum.melonds.domain.model.RomIconFiltering
import me.magnum.melonds.ui.common.FabActionItem
import me.magnum.melonds.ui.common.MultiActionFloatingActionButton
import me.magnum.melonds.ui.common.melonButtonColors
import me.magnum.melonds.ui.dsiwaremanager.model.DSiWareManagerUiState
import me.magnum.melonds.ui.romlist.RomIcon
import me.magnum.melonds.ui.settings.SettingsActivity
import me.magnum.melonds.ui.theme.MelonTheme

private const val FAB_ITEM_FROM_FILE = 1
private const val FAB_ITEM_FROM_ROM_LIST = 2

@Composable
fun DSiWareManager(
    modifier: Modifier,
    state: DSiWareManagerUiState,
    onImportTitle: (Uri) -> Unit,
    onDeleteTitle: (DSiWareTitle) -> Unit,
    onBiosConfigurationFinished: () -> Unit,
    retrieveTitleIcon: (DSiWareTitle) -> RomIcon,
) {
    val importTitleLauncher = rememberLauncherForActivityResult(FilePickerContract(Permission.READ)) {
        if (it != null) {
            onImportTitle(it)
        }
    }

    when (state) {
        is DSiWareManagerUiState.DSiSetupInvalid -> {
            InvalidSetup(
                modifier = modifier,
                configurationStatus = state.status,
                onBiosConfigurationFinished = onBiosConfigurationFinished,
            )
        }
        is DSiWareManagerUiState.Loading -> Loading(modifier)
        is DSiWareManagerUiState.Ready -> {
            Ready(
                modifier = modifier,
                titles = state.titles,
                onImportTitleFromFile = { importTitleLauncher.launch(null to arrayOf("*/*")) },
                onImportTitleFromRomList = { onImportTitle(it.uri) },
                onDeleteTitle = onDeleteTitle,
                retrieveTitleIcon = retrieveTitleIcon,
            )
        }
        is DSiWareManagerUiState.Error -> Error(modifier)
    }
}

@Composable
private fun InvalidSetup(modifier: Modifier, configurationStatus: ConfigurationDirResult.Status, onBiosConfigurationFinished: () -> Unit) {
    val context = LocalContext.current
    
    val biosSetupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onBiosConfigurationFinished()
    }

    Column(modifier.padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        when (configurationStatus) {
            ConfigurationDirResult.Status.UNSET -> {
                Text(
                    text = stringResource(R.string.dsiware_manager_no_dsi_setup),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val intent = Intent(context, SettingsActivity::class.java).apply {
                            putExtra(SettingsActivity.KEY_ENTRY_POINT, SettingsActivity.CUSTOM_FIRMWARE_ENTRY_POINT)
                        }
                        biosSetupLauncher.launch(intent)
                    },
                    colors = melonButtonColors(),
                ) {
                    Text(text = stringResource(R.string.dsiware_manager_setup).uppercase())
                }
            }
            ConfigurationDirResult.Status.INVALID -> {
                Text(
                    text = stringResource(R.string.dsiware_manager_invalid_dsi_setup),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val intent = Intent(context, SettingsActivity::class.java).apply {
                            putExtra(SettingsActivity.KEY_ENTRY_POINT, SettingsActivity.CUSTOM_FIRMWARE_ENTRY_POINT)
                        }
                        biosSetupLauncher.launch(intent)
                    },
                    colors = melonButtonColors(),
                ) {
                    Text(text = stringResource(R.string.dsiware_manager_fix_setup).uppercase())
                }
            }
            ConfigurationDirResult.Status.VALID -> {
            }
        }
    }
}

@Composable
private fun Loading(modifier: Modifier) {
    Box(modifier) {
        CircularProgressIndicator(
            modifier = Modifier.align(Alignment.Center),
            color = MaterialTheme.colors.secondary,
        )
    }
}

@Composable
private fun Ready(
    modifier: Modifier,
    titles: List<DSiWareTitle>,
    onImportTitleFromFile: () -> Unit,
    onImportTitleFromRomList: (Rom) -> Unit,
    onDeleteTitle: (DSiWareTitle) -> Unit,
    retrieveTitleIcon: (DSiWareTitle) -> RomIcon,
) {
    val showingRomList = rememberSaveable(null) { mutableStateOf(false) }

    Box(modifier = modifier) {
        if (titles.isEmpty()) {
            Text(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                text = stringResource(R.string.no_dsiware_titles_installed),
            )
        } else {
            DSiWareTitleList(
                modifier = Modifier.fillMaxSize(),
                titles = titles,
                onDeleteTitle = onDeleteTitle,
                retrieveTitleIcon = retrieveTitleIcon,
            )
        }

        MultiActionFloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
            actions = listOf(
                FabActionItem(FAB_ITEM_FROM_FILE, stringResource(id = R.string.dsiware_import_from_file), painterResource(id = R.drawable.ic_file)),
                FabActionItem(FAB_ITEM_FROM_ROM_LIST, stringResource(id = R.string.dsiware_import_from_rom_list), rememberVectorPainter(image = Icons.Filled.List)),
            ),
            onActionClicked = {
                when (it.id) {
                    FAB_ITEM_FROM_FILE -> onImportTitleFromFile()
                    FAB_ITEM_FROM_ROM_LIST -> showingRomList.value = true
                    else -> {}
                }
            }
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = stringResource(R.string.import_dsiware_title),
            )
        }
    }

    if (showingRomList.value) {
        DSiWareRomListDialog(
            onDismiss = { showingRomList.value = false },
            onRomSelected = {
                onImportTitleFromRomList(it)
                showingRomList.value = false
            },
        )
    }
}

@Composable
private fun Error(modifier: Modifier) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.dsiware_manager_load_error),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DSiWareTitleList(
    modifier: Modifier,
    titles: List<DSiWareTitle>,
    onDeleteTitle: (DSiWareTitle) -> Unit,
    retrieveTitleIcon: (DSiWareTitle) -> RomIcon,
) {
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
        Ready(
            modifier = Modifier.fillMaxSize(),
            titles = listOf(
                DSiWareTitle("Legit Game", "Notendo", 0, ByteArray(0)),
                DSiWareTitle("Legit Game: Snapped!", "Upasuft", 1, ByteArray(0)),
                DSiWareTitle("Highway 4 - Mediocre Racing", "Microware", 2, ByteArray(0)),
            ),
            onImportTitleFromFile = {},
            onImportTitleFromRomList = {},
            onDeleteTitle = {},
            retrieveTitleIcon = { RomIcon(bitmap, RomIconFiltering.NONE) },
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewDSiWareManagerInvalidSetup() {
    MelonTheme {
        InvalidSetup(
            modifier = Modifier.fillMaxSize(),
            configurationStatus = ConfigurationDirResult.Status.INVALID,
            onBiosConfigurationFinished = {},
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewDSiWareManagerError() {
    MelonTheme {
        Error(Modifier.fillMaxSize())
    }
}