package me.magnum.melonds.ui.dsiwaremanager.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.flow.collectLatest
import me.magnum.melonds.R
import me.magnum.melonds.common.Permission
import me.magnum.melonds.common.contracts.FilePickerContract
import me.magnum.melonds.domain.model.ConfigurationDirResult
import me.magnum.melonds.domain.model.DSiWareTitle
import me.magnum.melonds.domain.model.RomIconFiltering
import me.magnum.melonds.domain.model.dsinand.DSiWareTitleFileType
import me.magnum.melonds.domain.model.dsinand.ImportDSiWareTitleResult
import me.magnum.melonds.ui.common.FabActionItem
import me.magnum.melonds.ui.common.MultiActionFloatingActionButton
import me.magnum.melonds.ui.common.melonButtonColors
import me.magnum.melonds.ui.dsiwaremanager.DSiWareManagerViewModel
import me.magnum.melonds.ui.dsiwaremanager.model.DSiWareManagerUiState
import me.magnum.melonds.ui.dsiwaremanager.model.ImportExportDSiWareTitleFileEvent
import me.magnum.melonds.ui.romlist.RomIcon
import me.magnum.melonds.ui.settings.SettingsActivity
import me.magnum.melonds.ui.theme.MelonTheme

private const val FAB_ITEM_FROM_FILE = 1
private const val FAB_ITEM_FROM_ROM_LIST = 2

@Composable
fun DSiWareManagerScreen(
    viewModel: DSiWareManagerViewModel,
    onBackClick: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val importingTitle = viewModel.importingTitle.collectAsState(false)
    val context = LocalContext.current
    val showingRomList = rememberSaveable(null) { mutableStateOf(false) }
    val systemUiController = rememberSystemUiController()

    val importTitleFilePickLauncher = rememberDSiWareTitleImportFilePicker(
        onFilePicked = viewModel::importDSiWareTitleFile,
    )
    val exportTitleFilePickLauncher = rememberDSiWareTitleExportFilePicker(
        onFilePicked = viewModel::exportDSiWareTitleFile,
    )

    val importTitleLauncher = rememberLauncherForActivityResult(FilePickerContract(Permission.READ)) {
        if (it != null) {
            viewModel.importTitleToNand(it)
        }
    }

    systemUiController.setStatusBarColor(MaterialTheme.colors.primaryVariant)

    val currentState = state
    Scaffold(
        topBar = {
            Box(Modifier.background(MaterialTheme.colors.primaryVariant).statusBarsPadding()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.dsiware_manager)) },
                    backgroundColor = MaterialTheme.colors.primary,
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                painter = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack),
                                contentDescription = null,
                            )
                        }
                    },
                    windowInsets = WindowInsets.safeDrawing.exclude(WindowInsets(bottom = Int.MAX_VALUE)),
                )
            }
        },
        floatingActionButton = {
            if (currentState is DSiWareManagerUiState.Ready) {
                MultiActionFloatingActionButton(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars.exclude(WindowInsets(top = Int.MAX_VALUE, bottom = Int.MAX_VALUE))),
                    actions = listOf(
                        FabActionItem(
                            FAB_ITEM_FROM_FILE,
                            stringResource(id = R.string.dsiware_import_from_file),
                            rememberVectorPainter(Icons.AutoMirrored.Filled.InsertDriveFile)
                        ),
                        FabActionItem(
                            FAB_ITEM_FROM_ROM_LIST,
                            stringResource(id = R.string.dsiware_import_from_rom_list),
                            rememberVectorPainter(image = Icons.AutoMirrored.Filled.List)
                        ),
                    ),
                    onActionClicked = {
                        when (it.id) {
                            FAB_ITEM_FROM_FILE -> { importTitleLauncher.launch(null to arrayOf("*/*")) }
                            FAB_ITEM_FROM_ROM_LIST -> showingRomList.value = true
                            else -> {}
                        }
                    }
                ) {
                    Icon(
                        painter = rememberVectorPainter(Icons.Default.Add),
                        contentDescription = stringResource(R.string.import_dsiware_title),
                    )
                }
            }
        },
        backgroundColor = MaterialTheme.colors.surface,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        when (currentState) {
            is DSiWareManagerUiState.DSiSetupInvalid -> {
                InvalidSetup(
                    modifier = Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize(),
                    configurationStatus = currentState.status,
                    onBiosConfigurationFinished = viewModel::revalidateBiosConfiguration,
                )
            }
            is DSiWareManagerUiState.Loading -> Loading(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize())
            is DSiWareManagerUiState.Ready -> {
                Ready(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = padding,
                    titles = currentState.titles,
                    onDeleteTitle = viewModel::deleteTitle,
                    onImportTitleFile = importTitleFilePickLauncher::launch,
                    onExportTitleFile = exportTitleFilePickLauncher::launch,
                    retrieveTitleIcon = viewModel::getTitleIcon,
                )
            }
            is DSiWareManagerUiState.Error -> Error(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize())
        }
    }

    if (showingRomList.value) {
        DSiWareRomListDialog(
            onDismiss = { showingRomList.value = false },
            onRomSelected = {
                viewModel.importTitleToNand(it.uri)
                showingRomList.value = false
            },
        )
    }

    if (importingTitle.value) {
        Dialog(
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            onDismissRequest = { },
        ) {
            CircularProgressIndicator(color = MaterialTheme.colors.secondary)
        }
    }

    LaunchedEffect(null) {
        viewModel.importTitleError.collectLatest {
            Toast.makeText(context, getImportTitleResultMessage(context, it), Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(null) {
        viewModel.importExportFileEvent.collectLatest {
            Toast.makeText(context, getImportExportFileErrorMessage(context, it), Toast.LENGTH_SHORT).show()
        }
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
    contentPadding: PaddingValues,
    titles: List<DSiWareTitle>,
    onDeleteTitle: (DSiWareTitle) -> Unit,
    onImportTitleFile: (DSiWareTitle, DSiWareTitleFileType) -> Unit,
    onExportTitleFile: (DSiWareTitle, DSiWareTitleFileType) -> Unit,
    retrieveTitleIcon: (DSiWareTitle) -> RomIcon,
) {
    Box(modifier = modifier) {
        if (titles.isEmpty()) {
            Text(
                modifier = Modifier
                    .padding(contentPadding)
                    .consumeWindowInsets(contentPadding)
                    .align(Alignment.Center)
                    .padding(24.dp),
                text = stringResource(R.string.no_dsiware_titles_installed),
            )
        } else {
            DSiWareTitleList(
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                titles = titles,
                onDeleteTitle = onDeleteTitle,
                onImportTitleFile = onImportTitleFile,
                onExportTitleFile = onExportTitleFile,
                retrieveTitleIcon = retrieveTitleIcon,
            )
        }
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
    contentPadding: PaddingValues,
    titles: List<DSiWareTitle>,
    onDeleteTitle: (DSiWareTitle) -> Unit,
    onImportTitleFile: (DSiWareTitle, DSiWareTitleFileType) -> Unit,
    onExportTitleFile: (DSiWareTitle, DSiWareTitleFileType) -> Unit,
    retrieveTitleIcon: (DSiWareTitle) -> RomIcon,
) {
    LazyColumn(
        modifier = modifier.consumeWindowInsets(contentPadding),
        contentPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
            top = contentPadding.calculateTopPadding(),
            end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
            bottom = contentPadding.calculateBottomPadding() + 16.dp + 56.dp + 16.dp, // Add FAB size and FAB padding
        ),
    ) {
        items(
            items = titles,
            key = { it.titleId },
        ) { dSiWareTitle ->
            DSiWareItem(
                modifier = Modifier.fillMaxWidth(),
                item = dSiWareTitle,
                onDeleteClicked = { onDeleteTitle(dSiWareTitle) },
                onImportFile = { onImportTitleFile(dSiWareTitle, it) },
                onExportFile = { onExportTitleFile(dSiWareTitle, it) },
                retrieveTitleIcon = { retrieveTitleIcon(dSiWareTitle) },
            )
        }
    }
}

private fun getImportTitleResultMessage(context: Context, result: ImportDSiWareTitleResult): String {
    return when (result) {
        ImportDSiWareTitleResult.SUCCESS -> ""
        ImportDSiWareTitleResult.NAND_NOT_OPEN -> context.getString(R.string.dsiware_manager_import_title_error_open_nand_failed)
        ImportDSiWareTitleResult.ERROR_OPENING_FILE -> context.getString(R.string.dsiware_manager_import_title_error_open_file_failed)
        ImportDSiWareTitleResult.NOT_DSIWARE_TITLE -> context.getString(R.string.dsiware_manager_import_title_error_not_dsiware_title)
        ImportDSiWareTitleResult.TITLE_ALREADY_IMPORTED -> context.getString(R.string.dsiware_manager_import_title_error_title_already_imported)
        ImportDSiWareTitleResult.INSATLL_FAILED -> context.getString(R.string.dsiware_manager_import_title_error_insatll_failed)
        ImportDSiWareTitleResult.METADATA_FETCH_FAILED -> context.getString(R.string.dsiware_manager_import_title_error_metadat_fetch_failed)
        ImportDSiWareTitleResult.UNKNOWN -> context.getString(R.string.dsiware_manager_import_title_error_unknown)
    }
}

private fun getImportExportFileErrorMessage(context: Context, result: ImportExportDSiWareTitleFileEvent): String {
    return when (result) {
        is ImportExportDSiWareTitleFileEvent.ImportSuccess -> context.getString(R.string.dsiware_manager_import_file_success, result.fileName)
        is ImportExportDSiWareTitleFileEvent.ImportError -> context.getString(R.string.dsiware_manager_import_file_error)
        is ImportExportDSiWareTitleFileEvent.ExportSuccess -> context.getString(R.string.dsiware_manager_export_file_success, result.fileName)
        is ImportExportDSiWareTitleFileEvent.ExportError -> context.getString(R.string.dsiware_manager_export_file_error)
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
            contentPadding = PaddingValues(0.dp),
            titles = listOf(
                DSiWareTitle("Legit Game", "Notendo", 0, ByteArray(0), 0, 0, 0),
                DSiWareTitle("Legit Game: Snapped!", "Upasuft", 1, ByteArray(0), 0, 0, 0),
                DSiWareTitle("Highway 4 - Mediocre Racing", "Microware", 2, ByteArray(0), 0, 0, 0),
            ),
            onDeleteTitle = {},
            onImportTitleFile = { _, _ -> },
            onExportTitleFile = { _, _ -> },
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