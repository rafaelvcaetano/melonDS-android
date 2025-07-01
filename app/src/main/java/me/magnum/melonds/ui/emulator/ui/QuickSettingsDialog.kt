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
import me.magnum.melonds.domain.model.DsExternalScreen
import me.magnum.melonds.ui.theme.MelonTheme

/**
 * A dialog that allows the user to quickly change some emulator settings.
 *
 * @param currentScreen The currently selected external screen.
 * @param onScreenSelected Called when the user selects a new external screen.
 * @param onOpenInternalLayout Called when the user clicks the "Internal screen layout" button.
 * @param onOpenExternalLayout Called when the user clicks the "External screen layout" button.
 * @param onDismiss Called when the user dismisses the dialog.
 */
@Composable
fun QuickSettingsDialog(
    currentScreen: DsExternalScreen,
    onScreenSelected: (DsExternalScreen) -> Unit,
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
                    val options = listOf(DsExternalScreen.TOP, DsExternalScreen.BOTTOM, DsExternalScreen.CUSTOM)
                    options.forEach { screen ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = selectedScreen == screen, onClick = {
                                    selectedScreen = screen
                                    onScreenSelected(screen)
                                })
                        ) {
                            RadioButton(selected = selectedScreen == screen, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            val text = when (screen) {
                                DsExternalScreen.TOP -> R.string.top_screen
                                DsExternalScreen.BOTTOM -> R.string.bottom_screen
                                DsExternalScreen.CUSTOM -> R.string.custom_layout
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