package me.magnum.melonds.ui.common.preference

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import me.magnum.melonds.R
import me.magnum.melonds.ui.common.component.text.CaptionText
import me.magnum.melonds.ui.common.melonTextButtonColors

@Composable
fun SingleChoiceItem(
    name: String,
    value: String,
    items: List<String>,
    selectedItemIndex: Int,
    onItemSelected: (Int) -> Unit,
) {
    var isDialogShown by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isDialogShown = true }
            .focusable()
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.body1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        CaptionText(
            text = value,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    if (isDialogShown) {
        SingleChoiceDialog(
            title = name,
            items = items,
            selectedItemIndex = selectedItemIndex,
            onOptionSelected = onItemSelected,
            onDismissRequest = { isDialogShown = false },
        )
    }
}

@Composable
private fun SingleChoiceDialog(
    title: String,
    items: List<String>,
    selectedItemIndex: Int,
    onOptionSelected: (index: Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 64.dp)
                        .padding(start = 24.dp, end = 24.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        modifier = Modifier,
                        text = title,
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                LazyColumn {
                    itemsIndexed(items) { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onOptionSelected(index)
                                    onDismissRequest()
                                }
                                .heightIn(min = 48.dp)
                                .padding(start = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = index == selectedItemIndex,
                                onClick = null,
                            )
                            Spacer(Modifier.width(32.dp))
                            Text(text = item)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { onDismissRequest() },
                        colors = melonTextButtonColors(),
                    ) {
                        Text(
                            text = stringResource(id = R.string.cancel).uppercase(),
                            style = MaterialTheme.typography.button,
                            color = MaterialTheme.colors.secondary,
                        )
                    }
                }
            }
        }
    }
}