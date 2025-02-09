package me.magnum.melonds.ui.backgrounds.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BackgroundItem(
    background: Background,
    isSelected: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onClick: () -> Unit,
    onPreviewClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    BackgroundItemBase(
        backgroundId = background.id?.toString(),
        backgroundName = background.name,
        backgroundImage = rememberAsyncImagePainter(background),
        isSelected = isSelected,
        showOptions = true,
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope,
        onClick = onClick,
        onPreviewClick = onPreviewClick,
        onDeleteClick = onDeleteClick,
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NoneBackgroundItem(
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    BackgroundItemBase(
        backgroundId = null,
        backgroundName = stringResource(R.string.none),
        backgroundImage = painterResource(R.drawable.ic_block),
        isSelected = isSelected,
        showOptions = false,
        sharedTransitionScope = null,
        animatedContentScope = null,
        onClick = onClick,
        onPreviewClick = { },
        onDeleteClick = { },
    )
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun BackgroundItemBase(
    backgroundId: String?,
    backgroundName: String,
    backgroundImage: Painter,
    isSelected: Boolean,
    showOptions: Boolean,
    sharedTransitionScope: SharedTransitionScope?,
    animatedContentScope: AnimatedContentScope?,
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
                Box(Modifier.size(160.dp).background(Color.Gray))
            } else {
                if (sharedTransitionScope != null && animatedContentScope != null) {
                    with(sharedTransitionScope) {
                        Image(
                            modifier = Modifier
                                .sharedElement(
                                    state = sharedTransitionScope.rememberSharedContentState(backgroundId.orEmpty()),
                                    animatedVisibilityScope = animatedContentScope,
                                )
                                .size(180.dp),
                            painter = backgroundImage,
                            contentDescription = null,
                        )
                    }
                } else {
                    Image(
                        modifier = Modifier.size(180.dp),
                        painter = backgroundImage,
                        contentDescription = null,
                    )
                }
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

@OptIn(ExperimentalSharedTransitionApi::class)
@MelonPreviewSet
@Composable
private fun BackgroundItemPreview() {
    MelonTheme {
        BackgroundItemBase(
            backgroundId = "",
            backgroundName = "Background",
            backgroundImage = rememberAsyncImagePainter(Uri.EMPTY),
            isSelected = false,
            showOptions = true,
            sharedTransitionScope = null,
            animatedContentScope = null,
            onClick = { },
            onPreviewClick = { },
            onDeleteClick = { },
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@MelonPreviewSet
@Composable
private fun BackgroundItemSelectedPreview() {
    MelonTheme {
        BackgroundItemBase(
            backgroundId = "",
            backgroundName = "Background",
            backgroundImage = rememberAsyncImagePainter(Uri.EMPTY),
            isSelected = true,
            showOptions = true,
            sharedTransitionScope = null,
            animatedContentScope = null,
            onClick = { },
            onPreviewClick = { },
            onDeleteClick = { },
        )
    }
}