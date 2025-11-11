package me.magnum.melonds.ui.emulator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.DualScreenPreset
import me.magnum.melonds.ui.theme.MelonTheme

@Composable
fun DualScreenPresetsDialog(
    dualScreenPreset: DualScreenPreset,
    onDualScreenPresetSelected: (DualScreenPreset) -> Unit,
    keepAspectRatio: Boolean,
    onKeepAspectRatioChanged: (Boolean) -> Unit,
    isDualScreenIntegerScaleEnabled: Boolean,
    onDualScreenIntegerScaleChanged: (Boolean) -> Unit,
    internalFillHeight: Boolean,
    onInternalFillHeightChanged: (Boolean) -> Unit,
    internalFillWidth: Boolean,
    onInternalFillWidthChanged: (Boolean) -> Unit,
    externalFillHeight: Boolean,
    onExternalFillHeightChanged: (Boolean) -> Unit,
    externalFillWidth: Boolean,
    onExternalFillWidthChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var showFillAreaDialog by remember { mutableStateOf(false) }
    var fillAreaDialogEnabled by remember { mutableStateOf(false) }
    var internalFillHeightState by remember(internalFillHeight) { mutableStateOf(internalFillHeight) }
    var internalFillWidthState by remember(internalFillWidth) { mutableStateOf(internalFillWidth) }
    var externalFillHeightState by remember(externalFillHeight) { mutableStateOf(externalFillHeight) }
    var externalFillWidthState by remember(externalFillWidth) { mutableStateOf(externalFillWidth) }

    Dialog(onDismissRequest = onDismiss) {
        (LocalView.current.parent as DialogWindowProvider).window.setDimAmount(0.8f)
        DualScreenPresetsContent(
            dualScreenPreset = dualScreenPreset,
            onDualScreenPresetSelected = onDualScreenPresetSelected,
            keepAspectRatio = keepAspectRatio,
            onKeepAspectRatioChanged = onKeepAspectRatioChanged,
            isDualScreenIntegerScaleEnabled = isDualScreenIntegerScaleEnabled,
            onDualScreenIntegerScaleChanged = onDualScreenIntegerScaleChanged,
            internalFillHeight = internalFillHeightState,
            internalFillWidth = internalFillWidthState,
            externalFillHeight = externalFillHeightState,
            externalFillWidth = externalFillWidthState,
            onFillAreaOptionsClick = { enabled ->
                fillAreaDialogEnabled = enabled
                showFillAreaDialog = true
            },
        )
    }

    if (showFillAreaDialog) {
        DualScreenFillAreaDialog(
            internalFillHeight = internalFillHeightState,
            internalFillWidth = internalFillWidthState,
            externalFillHeight = externalFillHeightState,
            externalFillWidth = externalFillWidthState,
            fillOptionsEnabled = fillAreaDialogEnabled,
            onInternalFillHeightChanged = {
                internalFillHeightState = it
                onInternalFillHeightChanged(it)
            },
            onInternalFillWidthChanged = {
                internalFillWidthState = it
                onInternalFillWidthChanged(it)
            },
            onExternalFillHeightChanged = {
                externalFillHeightState = it
                onExternalFillHeightChanged(it)
            },
            onExternalFillWidthChanged = {
                externalFillWidthState = it
                onExternalFillWidthChanged(it)
            },
            onDismiss = { showFillAreaDialog = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DualScreenPresetsContent(
    dualScreenPreset: DualScreenPreset,
    onDualScreenPresetSelected: (DualScreenPreset) -> Unit,
    keepAspectRatio: Boolean,
    onKeepAspectRatioChanged: (Boolean) -> Unit,
    isDualScreenIntegerScaleEnabled: Boolean,
    onDualScreenIntegerScaleChanged: (Boolean) -> Unit,
    internalFillHeight: Boolean,
    internalFillWidth: Boolean,
    externalFillHeight: Boolean,
    externalFillWidth: Boolean,
    onFillAreaOptionsClick: (Boolean) -> Unit,
) {
    Card {
        var selectedPreset by remember(dualScreenPreset) { mutableStateOf(dualScreenPreset) }
        var keepAspect by remember(keepAspectRatio) { mutableStateOf(keepAspectRatio) }
        var integerScale by remember(isDualScreenIntegerScaleEnabled) { mutableStateOf(isDualScreenIntegerScaleEnabled) }

        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
            Text(
                modifier = Modifier.padding(horizontal = 24.dp),
                text = stringResource(R.string.presets),
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                modifier = Modifier.padding(horizontal = 24.dp),
                text = stringResource(R.string.dual_screen_presets),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            val presetOptions = listOf(
                DualScreenPreset.OFF,
                DualScreenPreset.INTERNAL_TOP_EXTERNAL_BOTTOM,
                DualScreenPreset.INTERNAL_BOTTOM_EXTERNAL_TOP,
            )
            Column(Modifier.selectableGroup()) {
                presetOptions.forEach { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .selectable(
                                selected = preset == selectedPreset,
                                onClick = {
                                    selectedPreset = preset
                                    onDualScreenPresetSelected(preset)
                                }
                            ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            text = when (preset) {
                                DualScreenPreset.OFF -> stringResource(R.string.dual_screen_preset_off)
                                DualScreenPreset.INTERNAL_TOP_EXTERNAL_BOTTOM -> stringResource(R.string.dual_screen_preset_internal_top_external_bottom)
                                DualScreenPreset.INTERNAL_BOTTOM_EXTERNAL_TOP -> stringResource(R.string.dual_screen_preset_internal_bottom_external_top)
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        RadioButton(
                            selected = preset == selectedPreset,
                            onClick = null,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = keepAspect, onValueChange = {
                        keepAspect = it
                        onKeepAspectRatioChanged(it)
                    }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 24.dp)
                        .padding(vertical = 8.dp),
                    text = stringResource(R.string.keep_ds_ratio),
                )
                Switch(checked = keepAspect, onCheckedChange = null)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (selectedPreset == DualScreenPreset.OFF) 0.5f else 1f)
                    .toggleable(
                        value = integerScale,
                        enabled = selectedPreset != DualScreenPreset.OFF,
                        onValueChange = {
                            integerScale = it
                            onDualScreenIntegerScaleChanged(it)
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 24.dp)
                        .padding(vertical = 8.dp),
                    text = stringResource(R.string.dual_screen_integer_scale),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Switch(
                    checked = integerScale,
                    onCheckedChange = null,
                    enabled = selectedPreset != DualScreenPreset.OFF,
                )
            }
            Spacer(Modifier.height(16.dp))
            val fillAreaEnabled = selectedPreset != DualScreenPreset.OFF && (integerScale || keepAspect)
            Button(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth(),
                enabled = fillAreaEnabled,
                onClick = { onFillAreaOptionsClick(fillAreaEnabled) },
            ) {
                Text(text = stringResource(R.string.dual_screen_fill_area_button))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                modifier = Modifier.padding(horizontal = 24.dp),
                text = stringResource(
                    R.string.dual_screen_fill_area_summary,
                    if (internalFillHeight) stringResource(R.string.on) else stringResource(R.string.off),
                    if (internalFillWidth) stringResource(R.string.on) else stringResource(R.string.off),
                    if (externalFillHeight) stringResource(R.string.on) else stringResource(R.string.off),
                    if (externalFillWidth) stringResource(R.string.on) else stringResource(R.string.off),
                ),
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DualScreenFillAreaDialog(
    internalFillHeight: Boolean,
    internalFillWidth: Boolean,
    externalFillHeight: Boolean,
    externalFillWidth: Boolean,
    fillOptionsEnabled: Boolean,
    onInternalFillHeightChanged: (Boolean) -> Unit,
    onInternalFillWidthChanged: (Boolean) -> Unit,
    onExternalFillHeightChanged: (Boolean) -> Unit,
    onExternalFillWidthChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        (LocalView.current.parent as DialogWindowProvider).window.setDimAmount(0.8f)
        Card {
            var internalHeight by remember(internalFillHeight) { mutableStateOf(internalFillHeight) }
            var internalWidth by remember(internalFillWidth) { mutableStateOf(internalFillWidth) }
            var externalHeight by remember(externalFillHeight) { mutableStateOf(externalFillHeight) }
            var externalWidth by remember(externalFillWidth) { mutableStateOf(externalFillWidth) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    text = stringResource(R.string.dual_screen_fill_area_title),
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    text = stringResource(R.string.dual_screen_fill_area_description),
                    style = MaterialTheme.typography.body2,
                )
                if (!fillOptionsEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.dual_screen_fill_area_requires_integer),
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                    )
                }
                Spacer(Modifier.height(16.dp))
                FillAreaToggleRow(
                    label = stringResource(R.string.dual_screen_internal_fill_height),
                    enabled = fillOptionsEnabled,
                    checked = internalHeight,
                    onCheckedChange = {
                        internalHeight = it
                        onInternalFillHeightChanged(it)
                    },
                )
                Spacer(Modifier.height(8.dp))
                FillAreaToggleRow(
                    label = stringResource(R.string.dual_screen_internal_fill_width),
                    enabled = fillOptionsEnabled,
                    checked = internalWidth,
                    onCheckedChange = {
                        internalWidth = it
                        onInternalFillWidthChanged(it)
                    },
                )
                Spacer(Modifier.height(16.dp))
                FillAreaToggleRow(
                    label = stringResource(R.string.dual_screen_external_fill_height),
                    enabled = fillOptionsEnabled,
                    checked = externalHeight,
                    onCheckedChange = {
                        externalHeight = it
                        onExternalFillHeightChanged(it)
                    },
                )
                Spacer(Modifier.height(8.dp))
                FillAreaToggleRow(
                    label = stringResource(R.string.dual_screen_external_fill_width),
                    enabled = fillOptionsEnabled,
                    checked = externalWidth,
                    onCheckedChange = {
                        externalWidth = it
                        onExternalFillWidthChanged(it)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FillAreaToggleRow(
    label: String,
    enabled: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .toggleable(
                value = checked,
                enabled = enabled,
                onValueChange = onCheckedChange,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp)
                .padding(vertical = 8.dp),
            text = label,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
        )
    }
}

@Preview
@Composable
private fun DualScreenPresetsContentPreview() {
    MelonTheme {
        DualScreenPresetsContent(
            dualScreenPreset = DualScreenPreset.INTERNAL_TOP_EXTERNAL_BOTTOM,
            onDualScreenPresetSelected = {},
            keepAspectRatio = true,
            onKeepAspectRatioChanged = {},
            isDualScreenIntegerScaleEnabled = true,
            onDualScreenIntegerScaleChanged = {},
            internalFillHeight = true,
            internalFillWidth = false,
            externalFillHeight = true,
            externalFillWidth = false,
            onFillAreaOptionsClick = {},
        )
    }
}
