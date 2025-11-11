package me.magnum.melonds.ui.emulator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.DualScreenPreset
import me.magnum.melonds.domain.model.ScreenAlignment
import me.magnum.melonds.domain.model.defaultExternalAlignment
import me.magnum.melonds.domain.model.defaultInternalAlignment
import me.magnum.melonds.ui.theme.MelonTheme

private val DualScreenDialogMinWidth = 360.dp

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
    internalVerticalAlignmentOverride: ScreenAlignment?,
    onInternalVerticalAlignmentOverrideChanged: (ScreenAlignment?) -> Unit,
    externalVerticalAlignmentOverride: ScreenAlignment?,
    onExternalVerticalAlignmentOverrideChanged: (ScreenAlignment?) -> Unit,
    onDismiss: () -> Unit,
) {
    var showFillAreaDialog by remember { mutableStateOf(false) }
    var fillAreaDialogEnabled by remember { mutableStateOf(false) }
    var internalFillHeightState by remember(internalFillHeight) { mutableStateOf(internalFillHeight) }
    var internalFillWidthState by remember(internalFillWidth) { mutableStateOf(internalFillWidth) }
    var externalFillHeightState by remember(externalFillHeight) { mutableStateOf(externalFillHeight) }
    var externalFillWidthState by remember(externalFillWidth) { mutableStateOf(externalFillWidth) }
    var showVerticalAlignmentDialog by remember { mutableStateOf(false) }
    var internalVerticalAlignmentState by remember(internalVerticalAlignmentOverride) { mutableStateOf(internalVerticalAlignmentOverride) }
    var externalVerticalAlignmentState by remember(externalVerticalAlignmentOverride) { mutableStateOf(externalVerticalAlignmentOverride) }

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
            internalVerticalAlignmentOverride = internalVerticalAlignmentState,
            externalVerticalAlignmentOverride = externalVerticalAlignmentState,
            onVerticalAlignmentOptionsClick = {
                showVerticalAlignmentDialog = true
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

    if (showVerticalAlignmentDialog) {
        DualScreenVerticalAlignmentDialog(
            preset = dualScreenPreset,
            internalAlignment = internalVerticalAlignmentState,
            externalAlignment = externalVerticalAlignmentState,
            onInternalAlignmentChanged = {
                internalVerticalAlignmentState = it
                onInternalVerticalAlignmentOverrideChanged(it)
            },
            onExternalAlignmentChanged = {
                externalVerticalAlignmentState = it
                onExternalVerticalAlignmentOverrideChanged(it)
            },
            onDismiss = { showVerticalAlignmentDialog = false },
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
    internalVerticalAlignmentOverride: ScreenAlignment?,
    externalVerticalAlignmentOverride: ScreenAlignment?,
    onVerticalAlignmentOptionsClick: () -> Unit,
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
            Spacer(Modifier.height(12.dp))
            val verticalAlignmentEnabled = fillAreaEnabled
            Button(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth(),
                enabled = verticalAlignmentEnabled,
                onClick = {
                    if (verticalAlignmentEnabled) {
                        onVerticalAlignmentOptionsClick()
                    }
                },
            ) {
                Text(text = stringResource(R.string.dual_screen_vertical_alignment_button))
            }
            Spacer(Modifier.height(8.dp))
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
        Card(modifier = Modifier.widthIn(min = DualScreenDialogMinWidth)) {
            var internalHeight by rememberSaveable { mutableStateOf(internalFillHeight) }
            var internalWidth by rememberSaveable { mutableStateOf(internalFillWidth) }
            var externalHeight by rememberSaveable { mutableStateOf(externalFillHeight) }
            var externalWidth by rememberSaveable { mutableStateOf(externalFillWidth) }
            var internalEnabled by rememberSaveable { mutableStateOf(internalFillHeight || internalFillWidth) }
            var externalEnabled by rememberSaveable { mutableStateOf(externalFillHeight || externalFillWidth) }

            LaunchedEffect(internalFillHeight) { internalHeight = internalFillHeight }
            LaunchedEffect(internalFillWidth) { internalWidth = internalFillWidth }
            LaunchedEffect(externalFillHeight) { externalHeight = externalFillHeight }
            LaunchedEffect(externalFillWidth) { externalWidth = externalFillWidth }

            fun setInternalEnabled(value: Boolean) {
                if (internalEnabled == value) return
                internalEnabled = value
                if (!value) {
                    onInternalFillHeightChanged(false)
                    onInternalFillWidthChanged(false)
                } else {
                    onInternalFillHeightChanged(internalHeight)
                    onInternalFillWidthChanged(internalWidth)
                }
            }

            fun setExternalEnabled(value: Boolean) {
                if (externalEnabled == value) return
                externalEnabled = value
                if (!value) {
                    onExternalFillHeightChanged(false)
                    onExternalFillWidthChanged(false)
                } else {
                    onExternalFillHeightChanged(externalHeight)
                    onExternalFillWidthChanged(externalWidth)
                }
            }

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
                FillAreaSection(
                    title = stringResource(R.string.dual_screen_fill_section_internal),
                    sectionEnabled = fillOptionsEnabled,
                    fillEnabled = internalEnabled,
                    fillHeight = internalHeight,
                    fillWidth = internalWidth,
                    onFillEnabledChanged = { setInternalEnabled(it) },
                    onFillHeightChanged = {
                        internalHeight = it
                        if (internalEnabled) {
                            onInternalFillHeightChanged(it)
                        }
                    },
                    onFillWidthChanged = {
                        internalWidth = it
                        if (internalEnabled) {
                            onInternalFillWidthChanged(it)
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                FillAreaSection(
                    title = stringResource(R.string.dual_screen_fill_section_external),
                    sectionEnabled = fillOptionsEnabled,
                    fillEnabled = externalEnabled,
                    fillHeight = externalHeight,
                    fillWidth = externalWidth,
                    onFillEnabledChanged = { setExternalEnabled(it) },
                    onFillHeightChanged = {
                        externalHeight = it
                        if (externalEnabled) {
                            onExternalFillHeightChanged(it)
                        }
                    },
                    onFillWidthChanged = {
                        externalWidth = it
                        if (externalEnabled) {
                            onExternalFillWidthChanged(it)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun FillAreaSection(
    title: String,
    sectionEnabled: Boolean,
    fillEnabled: Boolean,
    fillHeight: Boolean,
    fillWidth: Boolean,
    onFillEnabledChanged: (Boolean) -> Unit,
    onFillHeightChanged: (Boolean) -> Unit,
    onFillWidthChanged: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
            )
            Switch(
                checked = fillEnabled,
                onCheckedChange = { onFillEnabledChanged(it) },
                enabled = sectionEnabled,
            )
        }
        val childEnabled = sectionEnabled && fillEnabled
        val childAlpha = if (childEnabled) 1f else 0.5f
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .alpha(childAlpha),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = stringResource(R.string.dual_screen_fill_height_label))
            Switch(
                checked = fillHeight,
                onCheckedChange = { onFillHeightChanged(it) },
                enabled = childEnabled,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .alpha(childAlpha),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = stringResource(R.string.dual_screen_fill_width_label))
            Switch(
                checked = fillWidth,
                onCheckedChange = { onFillWidthChanged(it) },
                enabled = childEnabled,
            )
        }
    }
}

@Composable
private fun DualScreenVerticalAlignmentDialog(
    preset: DualScreenPreset,
    internalAlignment: ScreenAlignment?,
    externalAlignment: ScreenAlignment?,
    onInternalAlignmentChanged: (ScreenAlignment?) -> Unit,
    onExternalAlignmentChanged: (ScreenAlignment?) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        (LocalView.current.parent as DialogWindowProvider).window.setDimAmount(0.8f)
        Card {
            var internalSelection by remember(preset, internalAlignment) {
                mutableStateOf(internalAlignment ?: preset.defaultInternalAlignment())
            }
            var externalSelection by remember(preset, externalAlignment) {
                mutableStateOf(externalAlignment ?: preset.defaultExternalAlignment())
            }
            var internalEnabled by remember(internalAlignment) { mutableStateOf(internalAlignment != null) }
            var externalEnabled by remember(externalAlignment) { mutableStateOf(externalAlignment != null) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    text = stringResource(R.string.dual_screen_vertical_alignment_title),
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(16.dp))
                VerticalAlignmentSection(
                    title = stringResource(R.string.dual_screen_vertical_alignment_internal_label),
                    enabled = internalEnabled,
                    selection = internalSelection,
                    onEnabledChanged = { enabled ->
                        internalEnabled = enabled
                        if (enabled) {
                            onInternalAlignmentChanged(internalSelection)
                        } else {
                            onInternalAlignmentChanged(null)
                        }
                    },
                    onSelectionChanged = { alignment ->
                        internalSelection = alignment
                        if (internalEnabled) {
                            onInternalAlignmentChanged(alignment)
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                VerticalAlignmentSection(
                    title = stringResource(R.string.dual_screen_vertical_alignment_external_label),
                    enabled = externalEnabled,
                    selection = externalSelection,
                    onEnabledChanged = { enabled ->
                        externalEnabled = enabled
                        if (enabled) {
                            onExternalAlignmentChanged(externalSelection)
                        } else {
                            onExternalAlignmentChanged(null)
                        }
                    },
                    onSelectionChanged = { alignment ->
                        externalSelection = alignment
                        if (externalEnabled) {
                            onExternalAlignmentChanged(alignment)
                        }
                    },
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun VerticalAlignmentSection(
    title: String,
    enabled: Boolean,
    selection: ScreenAlignment,
    onEnabledChanged: (Boolean) -> Unit,
    onSelectionChanged: (ScreenAlignment) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
            )
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChanged,
            )
        }
        val options = listOf(ScreenAlignment.TOP, ScreenAlignment.CENTER, ScreenAlignment.BOTTOM)
        options.forEach { alignment ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (enabled) 1f else 0.5f)
                    .toggleable(
                        value = selection == alignment,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onValueChange = {
                            onSelectionChanged(alignment)
                        },
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selection == alignment,
                    onClick = null,
                    enabled = enabled,
                )
                Text(
                    modifier = Modifier.padding(start = 8.dp),
                    text = alignmentName(alignment),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun alignmentName(alignment: ScreenAlignment): String {
    return when (alignment) {
        ScreenAlignment.TOP -> stringResource(R.string.dual_screen_vertical_alignment_option_top)
        ScreenAlignment.CENTER -> stringResource(R.string.dual_screen_vertical_alignment_option_center)
        ScreenAlignment.BOTTOM -> stringResource(R.string.dual_screen_vertical_alignment_option_bottom)
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
            internalVerticalAlignmentOverride = ScreenAlignment.TOP,
            externalVerticalAlignmentOverride = ScreenAlignment.BOTTOM,
            onVerticalAlignmentOptionsClick = {},
        )
    }
}
