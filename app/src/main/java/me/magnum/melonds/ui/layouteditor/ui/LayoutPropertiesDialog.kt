package me.magnum.melonds.ui.layouteditor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.layout.LayoutConfiguration
import me.magnum.melonds.ui.common.component.dialog.BaseDialog
import me.magnum.melonds.ui.common.component.dialog.DialogButton
import me.magnum.melonds.ui.common.component.dialog.TextInputDialog
import me.magnum.melonds.ui.common.component.dialog.rememberTextInputDialogState
import me.magnum.melonds.ui.common.preference.ActionLauncherItem
import me.magnum.melonds.ui.common.preference.SeekBarItem
import me.magnum.melonds.ui.common.preference.SingleChoiceItem
import me.magnum.melonds.ui.common.preference.SwitchItem
import kotlin.math.roundToInt

@Composable
fun LayoutPropertiesDialog(
    layoutConfiguration: LayoutConfiguration,
    onDismiss: () -> Unit,
    onSave: (String?, LayoutConfiguration.LayoutOrientation, Boolean, Int) -> Unit,
) {
    val defaultName = stringResource(R.string.custom_layout_default_name)
    var name by rememberSaveable(layoutConfiguration.name) { mutableStateOf(layoutConfiguration.name) }
    var orientation by rememberSaveable(layoutConfiguration.orientation) { mutableStateOf(layoutConfiguration.orientation) }
    var useCustomOpacity by rememberSaveable(layoutConfiguration.useCustomOpacity) { mutableStateOf(layoutConfiguration.useCustomOpacity) }
    var opacity by rememberSaveable(layoutConfiguration.opacity) { mutableFloatStateOf(layoutConfiguration.opacity.toFloat()) }

    val textInputDialogState = rememberTextInputDialogState()
    var showOrientationDialog by rememberSaveable { mutableStateOf(false) }
    val orientationOptions = stringArrayResource(R.array.layout_orientation_options)

    BaseDialog(
        title = stringResource(R.string.properties),
        onDismiss = onDismiss,
        content = { padding ->
            ActionLauncherItem(
                name = stringResource(R.string.layout_name),
                value = name ?: stringResource(R.string.not_set),
                onLaunchAction = {
                    textInputDialogState.show(
                        initialText = name ?: defaultName,
                        onConfirm = { newName ->
                            if (newName.isNotBlank()) {
                                name = newName
                            }
                        },
                    )
                },
                horizontalPadding = padding.calculateStartPadding(LocalLayoutDirection.current),
            )

            SingleChoiceItem(
                name = stringResource(R.string.layout_orientation),
                value = orientationOptions.getOrElse(orientation.ordinal) { "" },
                items = orientationOptions.toList(),
                selectedItemIndex = orientation.ordinal,
                onItemSelected = { newOrientationIndex ->
                    orientation = LayoutConfiguration.LayoutOrientation.entries[newOrientationIndex]
                },
                horizontalPadding = padding.calculateStartPadding(LocalLayoutDirection.current),
            )

            SwitchItem(
                name = stringResource(R.string.layout_use_default_opacity),
                isOn = !useCustomOpacity,
                onToggle = { useCustomOpacity = !it },
                horizontalPadding = padding.calculateStartPadding(LocalLayoutDirection.current),
            )

            SeekBarItem(
                name = stringResource(R.string.layout_opacity),
                value = opacity,
                range = 0f..100f,
                enabled = useCustomOpacity,
                onValueChange = { opacity = it },
                horizontalPadding = padding.calculateStartPadding(LocalLayoutDirection.current),
            )
        },
        buttons = {
            DialogButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
            )
            DialogButton(
                text = stringResource(R.string.ok),
                onClick = {
                    onSave(name, orientation, useCustomOpacity, opacity.roundToInt())
                },
            )
        }
    )

    TextInputDialog(
        title = stringResource(R.string.layout_name),
        dialogState = textInputDialogState,
        allowEmpty = true,
    )

    if (showOrientationDialog) {
        BaseDialog(
            title = stringResource(R.string.layout_orientation),
            onDismiss = { showOrientationDialog = false },
            content = { padding ->
                Column(modifier = Modifier.padding(padding)) {
                    LayoutConfiguration.LayoutOrientation.entries.forEachIndexed { index, option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = option == orientation,
                                    onClick = {
                                        orientation = option
                                        showOrientationDialog = false
                                    },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = option == orientation,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colors.secondary),
                            )
                            Text(
                                text = orientationOptions.getOrElse(index) { "" },
                                modifier = Modifier.padding(start = 16.dp),
                                style = MaterialTheme.typography.body1,
                            )
                        }
                    }
                }
            },
            buttons = {
                DialogButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showOrientationDialog = false },
                )
            },
        )
    }
}
