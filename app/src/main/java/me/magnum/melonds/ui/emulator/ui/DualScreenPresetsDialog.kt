package me.magnum.melonds.ui.emulator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
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
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        (LocalView.current.parent as DialogWindowProvider).window.setDimAmount(0.8f)
        Content(
            dualScreenPreset = dualScreenPreset,
            onDualScreenPresetSelected = onDualScreenPresetSelected,
            keepAspectRatio = keepAspectRatio,
            onKeepAspectRatioChanged = onKeepAspectRatioChanged,
            isDualScreenIntegerScaleEnabled = isDualScreenIntegerScaleEnabled,
            onDualScreenIntegerScaleChanged = onDualScreenIntegerScaleChanged,
        )
    }
}

@Composable
private fun Content(
    dualScreenPreset: DualScreenPreset,
    onDualScreenPresetSelected: (DualScreenPreset) -> Unit,
    keepAspectRatio: Boolean,
    onKeepAspectRatioChanged: (Boolean) -> Unit,
    isDualScreenIntegerScaleEnabled: Boolean,
    onDualScreenIntegerScaleChanged: (Boolean) -> Unit,
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
                            .selectable(
                                selected = selectedPreset == preset,
                                onClick = {
                                    selectedPreset = preset
                                    onDualScreenPresetSelected(preset)
                                },
                            )
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        RadioButton(selected = selectedPreset == preset, onClick = null)
                        val text = when (preset) {
                            DualScreenPreset.OFF -> R.string.dual_screen_preset_off
                            DualScreenPreset.INTERNAL_TOP_EXTERNAL_BOTTOM -> R.string.dual_screen_preset_internal_top_external_bottom
                            DualScreenPreset.INTERNAL_BOTTOM_EXTERNAL_TOP -> R.string.dual_screen_preset_internal_bottom_external_top
                        }
                        Text(
                            text = stringResource(text),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = keepAspect,
                        onValueChange = {
                            keepAspect = it
                            onKeepAspectRatioChanged(it)
                        },
                    )
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(id = R.string.keep_ds_ratio),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Switch(
                    checked = keepAspect,
                    onCheckedChange = null,
                )
            }
            Spacer(Modifier.height(8.dp))
            val integerScaleEnabled = selectedPreset != DualScreenPreset.OFF
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (integerScaleEnabled) 1f else 0.5f)
                    .toggleable(
                        value = integerScale,
                        enabled = integerScaleEnabled,
                        onValueChange = {
                            integerScale = it
                            onDualScreenIntegerScaleChanged(it)
                        },
                    )
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.dual_screen_integer_scale),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Switch(
                    checked = integerScale,
                    onCheckedChange = null,
                    enabled = integerScaleEnabled,
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewDualScreenPresetsDialog() {
    MelonTheme {
        Content(
            dualScreenPreset = DualScreenPreset.INTERNAL_TOP_EXTERNAL_BOTTOM,
            onDualScreenPresetSelected = { },
            keepAspectRatio = true,
            onKeepAspectRatioChanged = { },
            isDualScreenIntegerScaleEnabled = true,
            onDualScreenIntegerScaleChanged = { },
        )
    }
}
