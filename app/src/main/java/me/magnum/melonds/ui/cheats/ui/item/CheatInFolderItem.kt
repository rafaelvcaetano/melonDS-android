package me.magnum.melonds.ui.cheats.ui.item

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Checkbox
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.domain.model.CheatInFolder
import me.magnum.melonds.ui.common.MelonPreviewSet
import me.magnum.melonds.ui.common.component.text.CaptionText
import me.magnum.melonds.ui.theme.MelonTheme

@Composable
fun CheatInFolderItem(
    modifier: Modifier,
    cheatInFolder: CheatInFolder,
    highlightQuery: String = "",
    onEditClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var showCheatOptions by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Checkbox(
            modifier = Modifier.padding(top = 4.dp),
            checked = cheatInFolder.cheat.enabled,
            onCheckedChange = null,
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                )
                HighlightedText(
                    modifier = Modifier.weight(1f),
                    text = cheatInFolder.folderName,
                    query = highlightQuery,
                    maxLines = 1,
                )
            }

            HighlightedText(
                text = cheatInFolder.cheat.name,
                query = highlightQuery,
            )
            if (cheatInFolder.cheat.description?.isNotBlank() == true) {
                CaptionText(
                    text = cheatInFolder.cheat.description,
                    style = MaterialTheme.typography.body2,
                )
            }
        }

        if (onEditClick != null || onDeleteClick != null) {
            CheatOptionsMenu(
                expanded = showCheatOptions,
                onExpandedChange = { showCheatOptions = it },
                onEditClick = onEditClick,
                onDeleteClick = onDeleteClick,
            )
        }
    }
}

@MelonPreviewSet
@Composable
private fun PreviewCheatInFolderItem() {
    MelonTheme {
        CheatInFolderItem(
            modifier = Modifier.fillMaxWidth(),
            cheatInFolder = CheatInFolder(
                cheat = Cheat(0, 0, "Some random cheat", "Press some buttons to activate this cheat. What does it do?", "", false),
                folderName = "Best cheats",
            ),
            onClick = { },
        )
    }
}
