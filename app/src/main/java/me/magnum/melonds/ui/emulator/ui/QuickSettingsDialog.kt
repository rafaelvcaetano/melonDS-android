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
import me.magnum.melonds.ui.theme.MelonTheme

@Composable
fun QuickSettingsDialog(
    currentScreen: DsExternalScreen,
    onScreenSelected: (DsExternalScreen) -> Unit,
    keepAspectRatio: Boolean,
    onKeepAspectRatioChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        (LocalView.current.parent as DialogWindowProvider).window.setDimAmount(0.8f)
        Content(
            currentScreen = currentScreen,
            onScreenSelected = onScreenSelected,
            keepAspectRatio = keepAspectRatio,
            onKeepAspectRatioChanged = onKeepAspectRatioChanged,
        )
    }
}

@Composable
private fun Content(
    currentScreen: DsExternalScreen,
    onScreenSelected: (DsExternalScreen) -> Unit,
    keepAspectRatio: Boolean,
    onKeepAspectRatioChanged: (Boolean) -> Unit,
) {
    Card {
        var selectedScreen by remember { mutableStateOf(currentScreen) }
        var keepAspect by remember { mutableStateOf(keepAspectRatio) }
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
            val options = listOf(DsExternalScreen.TOP, DsExternalScreen.BOTTOM)
            Column(Modifier.selectableGroup()) {
                options.forEach { screen ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedScreen == screen,
                                onClick = {
                                    selectedScreen = screen
                                    onScreenSelected(screen)
                                },
                            )
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        RadioButton(selected = selectedScreen == screen, onClick = null)

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
            if (selectedScreen != DsExternalScreen.CUSTOM) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .toggleable(keepAspect) {
                            keepAspect = it
                            onKeepAspectRatioChanged(it)
                        }
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
            keepAspectRatio = true,
            onKeepAspectRatioChanged = { },
        )
    }
}