package me.magnum.melonds.ui.cheats.ui.item

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Checkbox
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.ui.common.MelonPreviewSet
import me.magnum.melonds.ui.common.component.text.CaptionText
import me.magnum.melonds.ui.theme.MelonTheme

@Composable
fun CheatItem(
    modifier: Modifier,
    cheat: Cheat,
    onClick: () -> Unit,
) {
    val hasDescription = cheat.description?.isNotBlank() == true

    val contentAlpha = if (cheat.isValid()) ContentAlpha.high else ContentAlpha.disabled
    CompositionLocalProvider(LocalContentAlpha provides contentAlpha) {
        Row(
            modifier = modifier
                .clickable(enabled = cheat.isValid(), onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = if (hasDescription) Alignment.Top else Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
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
            Checkbox(
                modifier = if (hasDescription) Modifier.padding(top = 4.dp) else Modifier,
                checked = cheat.enabled,
                enabled = cheat.isValid(),
                onCheckedChange = null,
            )
        }
    }
}

@MelonPreviewSet
@Composable
private fun PreviewCheatItem() {
    MelonTheme {
        CheatItem(
            modifier = Modifier.fillMaxWidth(),
            cheat = Cheat(0, "Some random cheat", "Press some buttons to activate this cheat. What does it do?", "", false),
            onClick = { },
        )
    }
}