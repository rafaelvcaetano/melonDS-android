package me.magnum.melonds.ui.common.component.romlist

import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.RomIconFiltering
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.domain.model.rom.config.RomConfig
import me.magnum.melonds.ui.common.component.text.CaptionText
import me.magnum.melonds.ui.romlist.RomIcon
import me.magnum.melonds.ui.theme.MelonTheme

val DefaultRomItemPadding = PaddingValues(start = 8.dp, top = 8.dp, bottom = 8.dp)

private val DesaturationColorMatrix = ColorMatrix().apply { setToSaturation(0f) }
private const val DisabledAlpha = 0.5f

@Composable
fun RomItem(
    modifier: Modifier,
    item: Rom,
    enabled: Boolean = true,
    onClick: () -> Unit,
    retrieveTitleIcon: suspend () -> RomIcon,
    contentPadding: PaddingValues = DefaultRomItemPadding,
) {
    Column(
        modifier
            .let { if (enabled) it.clickable { onClick() } else it }
            .alpha(if (enabled) 1f else DisabledAlpha)
    ) {
        RomItemContent(
            rom = item,
            enabled = enabled,
            retrieveTitleIcon = retrieveTitleIcon,
            contentPadding = contentPadding,
        )
        Divider()
    }
}

@Composable
internal fun RomItemContent(
    modifier: Modifier = Modifier,
    rom: Rom,
    enabled: Boolean = true,
    retrieveTitleIcon: suspend () -> RomIcon,
    contentPadding: PaddingValues = DefaultRomItemPadding,
) {
    Row(
        modifier = modifier.height(IntrinsicSize.Min).padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var romIcon by remember {
            mutableStateOf<RomIcon?>(null)
        }
        LaunchedEffect(rom.hashCode()) {
            romIcon = retrieveTitleIcon()
        }

        val iconColorFilter = if (!enabled) {
            ColorFilter.colorMatrix(DesaturationColorMatrix)
        } else {
            null
        }

        Image(
            modifier = Modifier.size(48.dp).align(Alignment.CenterVertically),
            bitmap = romIcon?.bitmap?.asImageBitmap() ?: ImageBitmap(1, 1),
            contentDescription = null,
            colorFilter = iconColorFilter,
            filterQuality = when (romIcon?.filtering) {
                RomIconFiltering.NONE -> FilterQuality.None
                RomIconFiltering.LINEAR -> DrawScope.DefaultFilterQuality
                null -> DrawScope.DefaultFilterQuality
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (rom.isDsiWareTitle) {
                    // Base logo height on text size to allow it to scale with font scaling
                    val logoHeight = with(LocalDensity.current) { 12.sp.toDp() }
                    Image(
                        modifier = Modifier.height(logoHeight),
                        contentScale = ContentScale.FillHeight,
                        painter = painterResource(id = R.drawable.logo_dsiware),
                        contentDescription = null,
                        colorFilter = iconColorFilter,
                    )
                }
                Text(
                    text = rom.config.customName ?: rom.name,
                    style = MaterialTheme.typography.body1.copy(fontSize = 18.sp),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            }
            CaptionText(
                text = rom.fileName,
                style = MaterialTheme.typography.body2,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewRomItem() {
    val bitmap = createBitmap(1, 1).apply { this[0, 0] = 0xFF777777.toInt() }

    MelonTheme {
        RomItem(
            modifier = Modifier.fillMaxWidth(),
            item = Rom("Highway 4: Mediocre Racing", "Nontendo", "Highway_4.nds", Uri.EMPTY, Uri.EMPTY, RomConfig(), null, true, ""),
            onClick = { },
            retrieveTitleIcon = { RomIcon(bitmap, RomIconFiltering.NONE) }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewRomItemDisabled() {
    val bitmap = createBitmap(1, 1).apply { this[0, 0] = 0xFF777777.toInt() }

    MelonTheme {
        RomItem(
            modifier = Modifier.fillMaxWidth(),
            item = Rom("DSiWare Title", "Nontendo", "dsiware_title.nds", Uri.EMPTY, Uri.EMPTY, RomConfig(), null, true, ""),
            enabled = false,
            onClick = { },
            retrieveTitleIcon = { RomIcon(bitmap, RomIconFiltering.NONE) }
        )
    }
}