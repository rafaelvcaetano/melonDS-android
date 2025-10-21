package me.magnum.melonds.ui.romdetails.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.magnum.melonds.R
import me.magnum.melonds.common.Permission
import me.magnum.melonds.common.contracts.FilePickerContract
import me.magnum.melonds.domain.model.DsExternalScreen
import me.magnum.melonds.domain.model.rom.config.RuntimeConsoleType
import me.magnum.melonds.domain.model.rom.config.RuntimeMicSource
import me.magnum.melonds.ui.common.MelonPreviewSet
import me.magnum.melonds.ui.common.component.dialog.TextInputDialog
import me.magnum.melonds.ui.common.component.dialog.rememberTextInputDialogState
import me.magnum.melonds.ui.common.preference.ActionLauncherItem
import me.magnum.melonds.ui.common.preference.SingleChoiceItem
import me.magnum.melonds.ui.layouts.ExternalLayoutSelectorActivity
import me.magnum.melonds.ui.layouts.LayoutSelectorActivity
import me.magnum.melonds.ui.romdetails.model.RomConfigUiModel
import me.magnum.melonds.ui.romdetails.model.RomConfigUiState
import me.magnum.melonds.ui.romdetails.model.RomConfigUpdateEvent
import me.magnum.melonds.ui.romdetails.model.RomGbaSlotConfigUiModel
import me.magnum.melonds.ui.theme.MelonTheme
import java.util.UUID

@Composable
fun RomConfigUi(
    modifier: Modifier,
    contentPadding: PaddingValues,
    romName: String,
    romConfigUiState: RomConfigUiState,
    onConfigUpdate: (RomConfigUpdateEvent) -> Unit,
) {
    when (romConfigUiState) {
        is RomConfigUiState.Loading -> Loading(modifier.padding(contentPadding))
        is RomConfigUiState.Ready -> Content(
            modifier = modifier,
            contentPadding = contentPadding,
            romName = romName,
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
    contentPadding: PaddingValues,
    romName: String,
    romConfig: RomConfigUiModel,
    onConfigUpdate: (RomConfigUpdateEvent) -> Unit,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState())
            .padding(
                start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
            ),
    ) {
        val renameDialogState = rememberTextInputDialogState()
        ActionLauncherItem(
            name = stringResource(id = R.string.label_rom_config_custom_name),
            value = romConfig.customName ?: romName,
            onLaunchAction = {
                renameDialogState.show(romConfig.customName ?: romName) { newName ->
                    onConfigUpdate(RomConfigUpdateEvent.CustomNameUpdate(newName.ifBlank { null }))
                }
            }
        )
        TextInputDialog(
            title = stringResource(id = R.string.label_rom_config_custom_name),
            dialogState = renameDialogState,
            allowEmpty = true,
            onDelete = {
                onConfigUpdate(RomConfigUpdateEvent.CustomNameUpdate(null))
            },
        )

        val consoleOptions = stringArrayResource(id = R.array.game_runtime_console_type_options)
        SingleChoiceItem(
            name = stringResource(id = R.string.label_rom_config_console),
            value = consoleOptions[romConfig.runtimeConsoleType.ordinal],
            items = consoleOptions.toList(),
            selectedItemIndex = romConfig.runtimeConsoleType.ordinal,
            onItemSelected = {
                onConfigUpdate(RomConfigUpdateEvent.RuntimeConsoleUpdate(RuntimeConsoleType.entries[it]))
            }
        )

        val micSourceOptions = stringArrayResource(id = R.array.game_runtime_mic_source_options)
        SingleChoiceItem(
            name = stringResource(id = R.string.microphone_source),
            value = micSourceOptions[romConfig.runtimeMicSource.ordinal],
            items = micSourceOptions.toList(),
            selectedItemIndex = romConfig.runtimeMicSource.ordinal,
            onItemSelected = {
                onConfigUpdate(RomConfigUpdateEvent.RuntimeMicSourceUpdate(RuntimeMicSource.entries[it]))
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

        val externalScreenOptions = listOf(
            stringResource(id = R.string.use_global_preference),
            *stringArrayResource(R.array.external_display_screen_options),
        )
        val selectedExternalScreenIndex = romConfig.externalScreen?.ordinal?.plus(1) ?: 0
        SingleChoiceItem(
            name = stringResource(id = R.string.external_display_screen),
            value = externalScreenOptions[selectedExternalScreenIndex],
            items = externalScreenOptions,
            selectedItemIndex = selectedExternalScreenIndex,
            onItemSelected = { index ->
                val screen = when (index) {
                    1 -> DsExternalScreen.TOP
                    2 -> DsExternalScreen.BOTTOM
                    3 -> DsExternalScreen.CUSTOM
                    else -> null
                }
                onConfigUpdate(RomConfigUpdateEvent.ExternalScreenUpdate(screen))
            }
        )

        // Custom external layouts are disabled for now
        /*val externalLayoutSelectorLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val layoutId = result.data?.getStringExtra(LayoutSelectorActivity.KEY_SELECTED_LAYOUT_ID)?.let { UUID.fromString(it) }
                onConfigUpdate(RomConfigUpdateEvent.ExternalLayoutUpdate(layoutId))
            }
        }
        ActionLauncherItem(
            name = stringResource(id = R.string.external_screen_layout),
            value = romConfig.externalLayoutName ?: stringResource(id = R.string.use_global_layout),
            onLaunchAction = {
                val intent = Intent(context, ExternalLayoutSelectorActivity::class.java).apply {
                    putExtra(LayoutSelectorActivity.KEY_SELECTED_LAYOUT_ID, romConfig.externalLayoutId?.toString())
                }
                externalLayoutSelectorLauncher.launch(intent)
            }
        )*/

        val gbaSlotOptions = stringArrayResource(id = R.array.gba_slot_options)
        SingleChoiceItem(
            name = stringResource(id = R.string.label_rom_config_gba_slot),
            value = gbaSlotOptions[romConfig.gbaSlotConfig.type.ordinal],
            items = gbaSlotOptions.toList(),
            selectedItemIndex = romConfig.gbaSlotConfig.type.ordinal,
            onItemSelected = {
                onConfigUpdate(RomConfigUpdateEvent.GbaSlotTypeUpdated(RomGbaSlotConfigUiModel.Type.entries[it]))
            }
        )

        val gbaRomSelectorLauncher = rememberLauncherForActivityResult(FilePickerContract(Permission.READ)) { result ->
            if (result != null) {
                onConfigUpdate(RomConfigUpdateEvent.GbaRomPathUpdate(result))
            }
        }
        val gbaSaveSelectorLauncher = rememberLauncherForActivityResult(FilePickerContract(Permission.READ_WRITE)) { result ->
            if (result != null) {
                onConfigUpdate(RomConfigUpdateEvent.GbaSavePathUpdate(result))
            }
        }

        AnimatedVisibility(visible = romConfig.gbaSlotConfig.type == RomGbaSlotConfigUiModel.Type.GbaRom) {
            Column {
                ActionLauncherItem(
                    name = stringResource(id = R.string.label_rom_config_gba_rom_path),
                    value = romConfig.gbaSlotConfig.gbaRomPath ?: stringResource(id = R.string.not_set),
                    enabled = true,
                    onLaunchAction = {
                        gbaRomSelectorLauncher.launch(Pair(null, null))
                    }
                )

                ActionLauncherItem(
                    name = stringResource(id = R.string.label_rom_config_gba_save_path),
                    value = romConfig.gbaSlotConfig.gbaSavePath ?: stringResource(id = R.string.not_set),
                    enabled = true,
                    onLaunchAction = {
                        gbaSaveSelectorLauncher.launch(Pair(null, null))
                    }
                )
            }
        }

        Spacer(Modifier.height(contentPadding.calculateBottomPadding()))
    }
}

@MelonPreviewSet
@Composable
private fun PreviewRomConfigUi() {
    MelonTheme {
        RomConfigUi(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(0.dp),
            romName = "Professor Layton and the Unwound Future",
            romConfigUiState = RomConfigUiState.Ready(
                RomConfigUiModel(
                    layoutName = "Default",
                    externalLayoutName = "Default",
                    gbaSlotConfig = RomGbaSlotConfigUiModel(type = RomGbaSlotConfigUiModel.Type.GbaRom)
                ),
            ),
            onConfigUpdate = { },
        )
    }
}