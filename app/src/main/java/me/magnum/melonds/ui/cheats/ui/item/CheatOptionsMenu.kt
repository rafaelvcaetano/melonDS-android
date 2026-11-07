package me.magnum.melonds.ui.cheats.ui.item

import androidx.compose.material.ContentAlpha
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import me.magnum.melonds.R

/**
 * Dropdown menu to show options for edit/delete cheat
 */
@Composable
fun CheatOptionsMenu(
    modifier: Modifier = Modifier,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onEditClick: (() -> Unit)?,
    onDeleteClick: (() -> Unit)?,
) {
    CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.high) {
        IconButton(
            modifier = modifier,
            onClick = { onExpandedChange(true) },
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.options),
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
            ) {
                if (onEditClick != null) {
                    DropdownMenuItem(
                        onClick = {
                            onExpandedChange(false)
                            onEditClick()
                        },
                    ) {
                        Text(stringResource(R.string.edit))
                    }
                }
                if (onDeleteClick != null) {
                    DropdownMenuItem(
                        onClick = {
                            onExpandedChange(false)
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
