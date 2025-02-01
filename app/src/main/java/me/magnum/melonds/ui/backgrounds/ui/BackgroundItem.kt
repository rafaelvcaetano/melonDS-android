package me.magnum.melonds.ui.backgrounds.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Card
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.Background
import me.magnum.melonds.ui.common.MelonPreviewSet
import me.magnum.melonds.ui.theme.MelonTheme

@Composable
fun BackgroundItem(
    background: Background,
    isSelected: Boolean,
    onClick: () -> Unit,
    onPreviewClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    BackgroundItemBase(
        backgroundName = background.name,
        backgroundImage = rememberAsyncImagePainter(background),
        isSelected = isSelected,
        showOptions = true,
        onClick = onClick,
        onPreviewClick = onPreviewClick,
        onDeleteClick = onDeleteClick,
    )
}

@Composable
fun NoneBackgroundItem(
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    BackgroundItemBase(
        backgroundName = stringResource(R.string.none),
        backgroundImage = painterResource(R.drawable.ic_block),
        isSelected = isSelected,
        showOptions = false,
        onClick = onClick,
        onPreviewClick = { },
        onDeleteClick = { },
    )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun BackgroundItemBase(
    backgroundName: String,
    backgroundImage: Painter,
    isSelected: Boolean,
    showOptions: Boolean,
    onClick: () -> Unit,
    onPreviewClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    var isOptionsPopupVisible by remember { mutableStateOf(false) }

    Card(
        modifier = if (isSelected) {
            Modifier.border(
                width = 4.dp,
                color = MaterialTheme.colors.secondary,
                shape = MaterialTheme.shapes.medium,
            )
        } else {
            Modifier
        },
        elevation = 4.dp,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(4.dp).width(IntrinsicSize.Min),
        ) {
            if (LocalInspectionMode.current) {
                Box(Modifier.size(180.dp).background(Color.Gray))
            } else {
                Image(
                    modifier = Modifier.size(180.dp),
                    painter = backgroundImage,
                    contentDescription = null,
                )
                /*AsyncImage(
                    modifier = Modifier.size(180.dp),
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(background)
                        .build(),
                    contentDescription = null,
                )*/
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = backgroundName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showOptions) {
                    IconButton(
                        onClick = { isOptionsPopupVisible = true }
                    ) {
                        Icon(
                            painter = rememberVectorPainter(Icons.Filled.MoreVert),
                            contentDescription = stringResource(R.string.options),
                        )

                        DropdownMenu(
                            expanded = isOptionsPopupVisible,
                            onDismissRequest = { isOptionsPopupVisible = false },
                        ) {
                            DropdownMenuItem(
                                onClick = {
                                    isOptionsPopupVisible = false
                                    onPreviewClick()
                                },
                            ) {
                                Text(stringResource(R.string.preview))
                            }
                            DropdownMenuItem(
                                onClick = {
                                    isOptionsPopupVisible = false
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
}

@MelonPreviewSet
@Composable
private fun BackgroundItemPreview() {
    MelonTheme {
        BackgroundItem(
            background = Background(
                id = null,
                name = "Background",
                uri = Uri.EMPTY,
            ),
            isSelected = false,
            onClick = { },
            onPreviewClick = { },
            onDeleteClick = { },
        )
    }
}

@MelonPreviewSet
@Composable
private fun BackgroundItemSelectedPreview() {
    MelonTheme {
        BackgroundItem(
            background = Background(
                id = null,
                name = "Background",
                uri = Uri.EMPTY,
            ),
            isSelected = true,
            onClick = { },
            onPreviewClick = { },
            onDeleteClick = { },
        )
    }
}