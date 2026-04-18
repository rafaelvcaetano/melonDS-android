package me.magnum.melonds.ui.layouteditor.ui

import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.layout.BackgroundMode
import me.magnum.melonds.ui.common.component.dialog.BaseDialog
import me.magnum.melonds.ui.common.component.dialog.DialogButton
import me.magnum.melonds.ui.common.preference.ActionLauncherItem
import me.magnum.melonds.ui.common.preference.SingleChoiceItem
import java.util.UUID

@Composable
fun LayoutBackgroundDialog(
    backgroundId: UUID?,
    backgroundMode: BackgroundMode,
    loadBackgroundName: suspend (UUID) -> String?,
    onOpenBackgroundPicker: () -> Unit,
    onBackgroundModeUpdate: (BackgroundMode) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    var backgroundName by remember { mutableStateOf<String?>(null) }
    val currentLoadBackgroundName by rememberUpdatedState(loadBackgroundName)

    LaunchedEffect(backgroundId) {
        backgroundName = if (backgroundId != null) {
            currentLoadBackgroundName(backgroundId)
        } else {
            null
        }
    }

    val modeOptions = stringArrayResource(R.array.background_portrait_mode_options)

    BaseDialog(
        title = stringResource(R.string.layout_background_title),
        onDismiss = onDismiss,
        content = { padding ->
            ActionLauncherItem(
                name = stringResource(R.string.background_name),
                value = backgroundName ?: stringResource(R.string.none),
                onLaunchAction = onOpenBackgroundPicker,
                horizontalPadding = padding.calculateStartPadding(LocalLayoutDirection.current),
            )

            SingleChoiceItem(
                name = stringResource(R.string.background_mode),
                value = modeOptions.getOrElse(backgroundMode.ordinal) { "" },
                items = modeOptions.toList(),
                selectedItemIndex = backgroundMode.ordinal,
                onItemSelected = { onBackgroundModeUpdate(BackgroundMode.entries[it]) },
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
                onClick = onSave,
            )
        }
    )
}
