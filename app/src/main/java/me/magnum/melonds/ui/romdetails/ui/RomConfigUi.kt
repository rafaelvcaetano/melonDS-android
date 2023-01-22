package me.magnum.melonds.ui.romdetails.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import me.magnum.melonds.R
import me.magnum.melonds.common.Permission
import me.magnum.melonds.common.contracts.FilePickerContract
import me.magnum.melonds.domain.model.RuntimeConsoleType
import me.magnum.melonds.domain.model.RuntimeMicSource
import me.magnum.melonds.ui.common.MelonPreviewSet
import me.magnum.melonds.ui.common.melonSwitchColors
import me.magnum.melonds.ui.layouts.LayoutSelectorActivity
import me.magnum.melonds.ui.romdetails.model.RomConfigUiModel
import me.magnum.melonds.ui.romdetails.model.RomConfigUiState
import me.magnum.melonds.ui.romdetails.model.RomConfigUpdateEvent
import me.magnum.melonds.ui.theme.MelonTheme
import java.util.*

@Composable
fun RomConfigUi(
    modifier: Modifier,
    romConfigUiState: RomConfigUiState,
    onConfigUpdate: (RomConfigUpdateEvent) -> Unit,
) {
    when (romConfigUiState) {
        is RomConfigUiState.Loading -> Loading(modifier)
        is RomConfigUiState.Ready -> Content(
            modifier = modifier,
            romConfig = romConfigUiState.romConfigUiModel,
            onConfigUpdate = onConfigUpdate,
        )
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
private fun Content(
    modifier: Modifier,
    romConfig: RomConfigUiModel,
    onConfigUpdate: (RomConfigUpdateEvent) -> Unit,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        val consoleOptions = stringArrayResource(id = R.array.game_runtime_console_type_options)
        SingleChoiceItem(
            name = stringResource(id = R.string.label_rom_config_console),
            value = consoleOptions[romConfig.runtimeConsoleType.ordinal],
            items = consoleOptions.toList(),
            selectedItemIndex = romConfig.runtimeConsoleType.ordinal,
            onItemSelected = {
                onConfigUpdate(RomConfigUpdateEvent.RuntimeConsoleUpdate(RuntimeConsoleType.values()[it]))
            }
        )

        val micSourceOptions = stringArrayResource(id = R.array.game_runtime_mic_source_options)
        SingleChoiceItem(
            name = stringResource(id = R.string.microphone_source),
            value = micSourceOptions[romConfig.runtimeMicSource.ordinal],
            items = micSourceOptions.toList(),
            selectedItemIndex = romConfig.runtimeMicSource.ordinal,
            onItemSelected = {
                onConfigUpdate(RomConfigUpdateEvent.RuntimeMicSourceUpdate(RuntimeMicSource.values()[it]))
            }
        )

        val context = LocalContext.current
        val layoutSelectorLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val layoutId = result.data?.getStringExtra(LayoutSelectorActivity.KEY_SELECTED_LAYOUT_ID)?.let { UUID.fromString(it) }
                onConfigUpdate(RomConfigUpdateEvent.LayoutUpdate(layoutId))
            }
        }
        ActionLauncherItem(
            name = stringResource(id = R.string.controller_layout),
            value = romConfig.layoutName ?: stringResource(id = R.string.use_global_layout),
            onLaunchAction = {
                val intent = Intent(context, LayoutSelectorActivity::class.java).apply {
                    putExtra(LayoutSelectorActivity.KEY_SELECTED_LAYOUT_ID, romConfig.layoutId?.toString())
                }
                layoutSelectorLauncher.launch(intent)
            }
        )

        SwitchItem(
            name = stringResource(id = R.string.label_rom_config_load_gba_rom),
            isOn = romConfig.loadGbaCart,
            onToggle = {
                onConfigUpdate(RomConfigUpdateEvent.LoadGbaRomUpdate(it))
            },
        )

        val gbaRomSelectorLauncher = rememberLauncherForActivityResult(FilePickerContract(Permission.READ)) { result ->
            if (result != null) {
                onConfigUpdate(RomConfigUpdateEvent.GbaRomPathUpdate(result))
            }
        }
        ActionLauncherItem(
            name = stringResource(id = R.string.label_rom_config_gba_rom_path),
            value = romConfig.gbaCartPath ?: stringResource(id = R.string.not_set),
            enabled = romConfig.loadGbaCart,
            onLaunchAction = {
                gbaRomSelectorLauncher.launch(Pair(romConfig.gbaCartUri, null))
            }
        )

        val gbaSaveSelectorLauncher = rememberLauncherForActivityResult(FilePickerContract(Permission.READ_WRITE)) { result ->
            if (result != null) {
                onConfigUpdate(RomConfigUpdateEvent.GbaSavePathUpdate(result))
            }
        }
        ActionLauncherItem(
            name = stringResource(id = R.string.label_rom_config_gba_save_path),
            value = romConfig.gbaSavePath ?: stringResource(id = R.string.not_set),
            enabled = romConfig.loadGbaCart,
            onLaunchAction = {
                gbaSaveSelectorLauncher.launch(Pair(romConfig.gbaSaveUri, null))
            }
        )
    }
}

@Composable
private fun SingleChoiceItem(
    name: String,
    value: String,
    items: List<String>,
    selectedItemIndex: Int,
    onItemSelected: (Int) -> Unit,
) {
    var isDialogShown by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isDialogShown = true }
            .focusable()
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.body1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    if (isDialogShown) {
        SingleChoiceDialog(
            title = name,
            items = items,
            selectedItemIndex = selectedItemIndex,
            onOptionSelected = onItemSelected,
            onDismissRequest = { isDialogShown = false },
        )
    }
}

@Composable
private fun SingleChoiceDialog(
    title: String,
    items: List<String>,
    selectedItemIndex: Int,
    onOptionSelected: (index: Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 64.dp)
                        .padding(start = 24.dp, end = 24.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        modifier = Modifier,
                        text = title,
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                LazyColumn {
                    itemsIndexed(items) { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onOptionSelected(index)
                                    onDismissRequest()
                                }
                                .heightIn(min = 48.dp)
                                .padding(start = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = index == selectedItemIndex,
                                onClick = null,
                            )
                            Spacer(Modifier.width(32.dp))
                            Text(text = item)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { onDismissRequest() }) {
                        Text(
                            text = stringResource(id = R.string.cancel).uppercase(),
                            style = MaterialTheme.typography.button,
                            color = MaterialTheme.colors.secondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionLauncherItem(
    name: String,
    value: String,
    enabled: Boolean = true,
    onLaunchAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .run {
                if (enabled) {
                    clickable { onLaunchAction() }.focusable()
                } else {
                    this
                }
            }
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.body1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.typography.body1.color.copy(alpha = if (enabled) ContentAlpha.high else ContentAlpha.disabled)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.typography.caption.color.copy(alpha = if (enabled) ContentAlpha.high else ContentAlpha.disabled)
        )
    }
}

@Composable
private fun SwitchItem(
    name: String,
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!isOn) }
            .focusable()
            .heightIn(min = 48.dp)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = name,
            style = MaterialTheme.typography.body1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = isOn,
            onCheckedChange = null,
            colors = melonSwitchColors(),
        )
    }
}

@MelonPreviewSet
@Composable
private fun PreviewRomConfigUi() {
    MelonTheme {
        RomConfigUi(
            modifier = Modifier.fillMaxSize(),
            romConfigUiState = RomConfigUiState.Ready(
                RomConfigUiModel(
                    layoutName = "Default",
                ),
            ),
            onConfigUpdate = { },
        )
    }
}