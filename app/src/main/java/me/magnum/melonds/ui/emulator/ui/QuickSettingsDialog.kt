package me.magnum.melonds.ui.emulator.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.DsScreen
import me.magnum.melonds.ui.theme.MelonTheme

@Composable
fun QuickSettingsDialog(
    currentScreen: DsScreen,
    onScreenSelected: (DsScreen) -> Unit,
    onOpenInternalLayout: () -> Unit,
    onOpenExternalLayout: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        (LocalView.current.parent as DialogWindowProvider).window.setDimAmount(0.8f)
        MelonTheme(isDarkTheme = true) {
            Surface {
                var selectedScreen by remember { mutableStateOf(currentScreen) }
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringResource(R.string.quick_settings), style = MaterialTheme.typography.h6)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.external_display_screen))
                    val options = listOf(DsScreen.TOP, DsScreen.BOTTOM, DsScreen.CUSTOM)
                    options.forEach { screen ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = selectedScreen == screen, onClick = {
                                    selectedScreen = screen
                                    onScreenSelected(screen)
                                })
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = selectedScreen == screen, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            val text = when (screen) {
                                DsScreen.TOP -> R.string.top_screen
                                DsScreen.BOTTOM -> R.string.bottom_screen
                                DsScreen.CUSTOM -> R.string.custom_layout
                            }
                            Text(stringResource(text), modifier = Modifier.align(Alignment.CenterVertically))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onOpenInternalLayout, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.internal_screen_layout))
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onOpenExternalLayout, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.external_screen_layout))
                    }
                }
            }
        }
    }
}
