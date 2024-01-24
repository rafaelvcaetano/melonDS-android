package me.magnum.melonds.ui.dsiwaremanager.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.DSiWareTitle
import me.magnum.melonds.domain.model.RomIconFiltering
import me.magnum.melonds.ui.common.component.text.CaptionText
import me.magnum.melonds.ui.dsiwaremanager.model.DSiWareItemDropdownMenu
import me.magnum.melonds.domain.model.dsinand.DSiWareTitleFileType
import me.magnum.melonds.ui.romlist.RomIcon
import me.magnum.melonds.ui.theme.MelonTheme

@Composable
fun DSiWareItem(
    modifier: Modifier,
    item: DSiWareTitle,
    onDeleteClicked: () -> Unit,
    onImportFile: (DSiWareTitleFileType) -> Unit,
    onExportFile: (DSiWareTitleFileType) -> Unit,
    retrieveTitleIcon: () -> RomIcon,
) {
    var dropdownMenu by remember(item) {
        mutableStateOf(DSiWareItemDropdownMenu.NONE)
    }

    Column(modifier) {
        Row(
            Modifier
                .height(IntrinsicSize.Min)
                .padding(start = 8.dp, top = 8.dp, bottom = 8.dp)) {
            val icon = remember(item.titleId) {
                retrieveTitleIcon()
            }

            Image(
                modifier = Modifier
                    .size(48.dp)
                    .align(CenterVertically),
                bitmap = icon.bitmap?.asImageBitmap() ?: ImageBitmap(1, 1),
                contentDescription = null,
                filterQuality = when (icon.filtering) {
                    RomIconFiltering.NONE -> FilterQuality.None
                    RomIconFiltering.LINEAR -> DrawScope.DefaultFilterQuality
                },
            )
            Spacer(Modifier.width(8.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.body1.copy(fontSize = 18.sp),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
                CaptionText(
                    text = item.producer,
                    style = MaterialTheme.typography.body2,
                )
            }
            IconButton(onClick = { dropdownMenu = DSiWareItemDropdownMenu.MAIN }) {
                Icon(
                    modifier = Modifier.size(32.dp),
                    painter = painterResource(id = R.drawable.ic_menu),
                    contentDescription = stringResource(id = R.string.delete),
                    tint = MaterialTheme.colors.onSurface,
                )

                ItemDropdownMenu(
                    item = item,
                    menu = dropdownMenu,
                    onOpenMenu = { dropdownMenu = it },
                    onDeleteItem = onDeleteClicked,
                    onImportFile = {
                        dropdownMenu = DSiWareItemDropdownMenu.NONE
                        onImportFile(it)
                    },
                    onExportFile = {
                        dropdownMenu = DSiWareItemDropdownMenu.NONE
                        onExportFile(it)
                    },
                )
            }

        }
        Divider()
    }
}

@Composable
private fun ItemDropdownMenu(
    item: DSiWareTitle,
    menu: DSiWareItemDropdownMenu,
    onOpenMenu: (DSiWareItemDropdownMenu) -> Unit,
    onDeleteItem: () -> Unit,
    onImportFile: (DSiWareTitleFileType) -> Unit,
    onExportFile: (DSiWareTitleFileType) -> Unit,
) {
    when (menu) {
        DSiWareItemDropdownMenu.NONE -> { /* no-op */ }
        DSiWareItemDropdownMenu.MAIN -> {
            DropdownMenu(
                expanded = true,
                onDismissRequest = { onOpenMenu(DSiWareItemDropdownMenu.NONE) },
            ) {
                DropdownMenuItem(onClick = { onOpenMenu(DSiWareItemDropdownMenu.IMPORT) }) {
                    Text(text = stringResource(id = R.string.dsiware_manager_import_data))
                }
                DropdownMenuItem(onClick = { onOpenMenu(DSiWareItemDropdownMenu.EXPORT) }) {
                    Text(text = stringResource(id = R.string.dsiware_manager_export_data))
                }
                DropdownMenuItem(onClick = onDeleteItem) {
                    Text(text = stringResource(id = R.string.delete))
                }
            }
        }
        DSiWareItemDropdownMenu.IMPORT -> {
            DropdownMenu(
                expanded = true,
                onDismissRequest = { onOpenMenu(DSiWareItemDropdownMenu.NONE) },
            ) {
                FileTypeDropdownItem(
                    fileType = DSiWareTitleFileType.PUBLIC_SAV,
                    enabled = item.hasPublicSavFile(),
                    onClick = { onImportFile(DSiWareTitleFileType.PUBLIC_SAV) },
                )
                FileTypeDropdownItem(
                    fileType = DSiWareTitleFileType.PRIVATE_SAV,
                    enabled = item.hasPrivateSavFile(),
                    onClick = { onImportFile(DSiWareTitleFileType.PRIVATE_SAV) },
                )
                FileTypeDropdownItem(
                    fileType = DSiWareTitleFileType.BANNER_SAV,
                    enabled = item.hasBannerSavFile(),
                    onClick = { onImportFile(DSiWareTitleFileType.BANNER_SAV) },
                )
            }
        }
        DSiWareItemDropdownMenu.EXPORT -> {
            DropdownMenu(
                expanded = true,
                onDismissRequest = { onOpenMenu(DSiWareItemDropdownMenu.NONE) },
            ) {
                FileTypeDropdownItem(
                    fileType = DSiWareTitleFileType.PUBLIC_SAV,
                    enabled = item.hasPublicSavFile(),
                    onClick = { onExportFile(DSiWareTitleFileType.PUBLIC_SAV) },
                )
                FileTypeDropdownItem(
                    fileType = DSiWareTitleFileType.PRIVATE_SAV,
                    enabled = item.hasPrivateSavFile(),
                    onClick = { onExportFile(DSiWareTitleFileType.PRIVATE_SAV) },
                )
                FileTypeDropdownItem(
                    fileType = DSiWareTitleFileType.BANNER_SAV,
                    enabled = item.hasBannerSavFile(),
                    onClick = { onExportFile(DSiWareTitleFileType.BANNER_SAV) },
                )
            }
        }
    }
}

@Composable
private fun FileTypeDropdownItem(fileType: DSiWareTitleFileType, enabled: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        onClick = onClick,
        enabled = enabled,
    ) {
        Text(text = fileType.fileName)
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun PreviewDSiWareItem() {
    val bitmap = createBitmap(1, 1).apply { this[0, 0] = 0xFF777777.toInt() }

    MelonTheme {
        DSiWareItem(
            modifier = Modifier.fillMaxWidth(),
            item = DSiWareTitle("Highway 4: Mediocre Racing", "Playpark", 0, ByteArray(0), 0, 0, 0),
            onDeleteClicked = { },
            onImportFile = { },
            onExportFile = { },
            retrieveTitleIcon = { RomIcon(bitmap, RomIconFiltering.NONE) },
        )
    }
}