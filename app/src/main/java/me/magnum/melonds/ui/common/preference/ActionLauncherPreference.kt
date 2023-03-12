package me.magnum.melonds.ui.common.preference

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material.ContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ActionLauncherItem(
    name: String,
    value: String,
    enabled: Boolean = true,
    onLaunchAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .run {
                if (enabled) {
                    clickable { onLaunchAction() }.focusable()
                } else {
                    this
                }
            }
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.body1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.typography.body1.color.copy(alpha = if (enabled) ContentAlpha.high else ContentAlpha.disabled)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.typography.caption.color.copy(alpha = if (enabled) ContentAlpha.medium else ContentAlpha.disabled)
        )
    }
}