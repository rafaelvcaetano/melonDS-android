package me.magnum.melonds.ui.common.component.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.magnum.melonds.ui.common.melonTextButtonColors

@Composable
fun BaseDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
    buttons: (@Composable () -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(decorFitsSystemWindows = false),
    ) {
        Card(Modifier.fillMaxWidth().safeDrawingPadding()) {
            Column(Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 64.dp)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Column(Modifier.verticalScroll(rememberScrollState())) {
                    content(PaddingValues(horizontal = 24.dp))

                    buttons?.let {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(start = 24.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                        ) {
                            buttons()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DialogButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = melonTextButtonColors(),
    ) {
        Text(text.uppercase())
    }
}