package me.magnum.melonds.ui.cheats.ui.item

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Checkbox
import androidx.compose.material.ContentAlpha
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.ui.common.MelonPreviewSet
import me.magnum.melonds.ui.common.component.text.CaptionText
import me.magnum.melonds.ui.theme.MelonTheme

@Composable
fun CheatItem(
    modifier: Modifier,
    cheat: Cheat,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val hasDescription = cheat.description?.isNotBlank() == true

    val contentAlpha = if (cheat.isValid()) ContentAlpha.high else ContentAlpha.disabled
    val verticalPadding = if (hasDescription) 12.dp else 4.dp
    CompositionLocalProvider(LocalContentAlpha provides contentAlpha) {
        Row(
            modifier = modifier
                .clickable(enabled = cheat.isValid(), onClick = onClick)
                .padding(start = 16.dp, top = verticalPadding, bottom = verticalPadding),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = if (hasDescription) Alignment.Top else Alignment.CenterVertically,
        ) {
            Checkbox(
                modifier = if (hasDescription) Modifier.padding(top = 4.dp) else Modifier,
                checked = cheat.enabled,
                enabled = cheat.isValid(),
                onCheckedChange = null,
            )

            Column(
                modifier = Modifier.weight(1f).padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(cheat.name)

                if (hasDescription) {
                    CaptionText(
                        style = MaterialTheme.typography.body2,
                        text = cheat.description.orEmpty(),
                    )
                }
            }

            var showCheatOptions by remember { mutableStateOf(false) }
            // Cheat can always be edited even if it's not valid
            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.high) {
                IconButton(onClick = { showCheatOptions = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.options),
                    )

                    DropdownMenu(
                        expanded = showCheatOptions,
                        onDismissRequest = { showCheatOptions = false },
                    ) {
                        DropdownMenuItem(
                            onClick = {
                                showCheatOptions = false
                                onEditClick()
                            },
                        ) {
                            Text(stringResource(R.string.edit))
                        }
                        DropdownMenuItem(
                            onClick = {
                                showCheatOptions = false
                                onDeleteClick()
                            },
                        ) {
                            Text(stringResource(R.string.delete))
                        }
                    }
                }
            }
        }
    }
}

@MelonPreviewSet
@Composable
private fun PreviewCheatItem() {
    MelonTheme {
        CheatItem(
            modifier = Modifier.fillMaxWidth(),
            cheat = Cheat(0, 0, "Some random cheat", "Press some buttons to activate this cheat. What does it do?", "", false),
            onClick = { },
            onEditClick = { },
            onDeleteClick = { },
        )
    }
}