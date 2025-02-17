package me.magnum.melonds.ui.cheats.ui.item

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.CheatFolder
import me.magnum.melonds.ui.common.MelonPreviewSet
import me.magnum.melonds.ui.theme.MelonTheme

@Composable
fun FolderItem(
    modifier: Modifier,
    folder: CheatFolder,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.padding(vertical = 12.dp).size(24.dp),
            painter = painterResource(id = R.drawable.ic_folder),
            contentDescription = null,
            tint = MaterialTheme.colors.secondary,
        )
        Text(
            modifier = Modifier.weight(1f),
            text = folder.name,
        )
    }
}

@MelonPreviewSet
@Composable
private fun PreviewFolderItem() {
    MelonTheme {
        FolderItem(
            modifier = Modifier.fillMaxWidth(),
            folder = CheatFolder(0, "Some random cheats", emptyList()),
            onClick = { },
        )
    }
}