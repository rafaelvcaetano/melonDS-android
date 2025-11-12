package me.magnum.melonds.ui.emulator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.ContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
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
import me.magnum.melonds.domain.model.DsExternalScreen
import me.magnum.melonds.domain.model.DualScreenPreset
import me.magnum.melonds.ui.theme.MelonTheme

@Composable
fun QuickSettingsDialog(
    currentScreen: DsExternalScreen,
    onScreenSelected: (DsExternalScreen) -> Unit,
    dualScreenPreset: DualScreenPreset,
    onOpenInternalLayout: () -> Unit,
    onOpenExternalLayout: () -> Unit,
    onRefreshExternalScreen: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        (LocalView.current.parent as DialogWindowProvider).window.setDimAmount(0.8f)
        Content(
            currentScreen = currentScreen,
            onScreenSelected = onScreenSelected,
            dualScreenPreset = dualScreenPreset,
            onOpenInternalLayout = onOpenInternalLayout,
            onOpenExternalLayout = onOpenExternalLayout,
            onRefreshExternalScreen = onRefreshExternalScreen,
        )
    }
}

@Composable
private fun Content(
    currentScreen: DsExternalScreen,
    onScreenSelected: (DsExternalScreen) -> Unit,
    dualScreenPreset: DualScreenPreset,
    onOpenInternalLayout: () -> Unit,
    onOpenExternalLayout: () -> Unit,
    onRefreshExternalScreen: () -> Unit,
) {
    Card {
        var selectedScreen by remember(currentScreen) { mutableStateOf(currentScreen) }
        val selectedPreset = dualScreenPreset
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
            Text(
                modifier = Modifier.padding(horizontal = 24.dp),
                text = stringResource(R.string.quick_settings),
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))

            Text(
                modifier = Modifier.padding(horizontal = 24.dp),
                text = stringResource(R.string.external_display_screen),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            val options = listOf(DsExternalScreen.TOP, DsExternalScreen.BOTTOM, DsExternalScreen.CUSTOM)
            val screenSelectionEnabled = selectedPreset == DualScreenPreset.OFF
            Column(Modifier.selectableGroup()) {
                options.forEach { screen ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (screenSelectionEnabled) 1f else 0.5f)
                            .selectable(
                                selected = selectedScreen == screen,
                                enabled = screenSelectionEnabled,
                                onClick = {
                                    selectedScreen = screen
                                    onScreenSelected(screen)
                                },
                            )
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        RadioButton(
                            selected = selectedScreen == screen,
                            onClick = null,
                            enabled = screenSelectionEnabled,
                        )

                        val text = when (screen) {
                            DsExternalScreen.TOP -> R.string.top_screen
                            DsExternalScreen.BOTTOM -> R.string.bottom_screen
                            DsExternalScreen.CUSTOM -> R.string.custom_layout
                        }
                        Text(
                            text = stringResource(text),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                val layoutEditorsEnabled = selectedPreset == DualScreenPreset.OFF
                Button(
                    onClick = onOpenInternalLayout,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = layoutEditorsEnabled,
                ) {
                    Text(stringResource(R.string.internal_screen_layout))
                }
                if (selectedScreen == DsExternalScreen.CUSTOM) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onOpenExternalLayout,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = layoutEditorsEnabled,
                    ) {
                        Text(stringResource(R.string.external_screen_layout))
                    }
                }
                if (!layoutEditorsEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.layout_editing_disabled_by_presets),
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onRefreshExternalScreen, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.refresh_external_screen))
                }
            }

        }
    }
}

@Preview
@Composable
private fun PreviewQuickSettingsDialog() {
    MelonTheme {
        Content(
            currentScreen = DsExternalScreen.TOP,
            onScreenSelected = { },
            dualScreenPreset = DualScreenPreset.OFF,
            onOpenInternalLayout = { },
            onOpenExternalLayout = { },
            onRefreshExternalScreen = { },
        )
    }
}
