package me.magnum.melonds.ui.dsiwaremanager.ui

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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.Rom
import me.magnum.melonds.domain.model.RomConfig
import me.magnum.melonds.domain.model.RomIconFiltering
import me.magnum.melonds.ui.romlist.RomIcon
import me.magnum.melonds.ui.theme.MelonTheme

val DefaultRomItemPadding = PaddingValues(start = 8.dp, top = 8.dp, bottom = 8.dp)

@Composable
fun RomItem(
    modifier: Modifier,
    item: Rom,
    onClick: () -> Unit,
    retrieveTitleIcon: suspend () -> RomIcon,
    contentPadding: PaddingValues = DefaultRomItemPadding,
) {
    Column(modifier.clickable { onClick() }) {
        Row(
            Modifier
                .height(IntrinsicSize.Min)
                .padding(contentPadding)
        ) {
            var romIcon by remember {
                mutableStateOf<RomIcon?>(null)
            }
            LaunchedEffect(item.hashCode()) {
                romIcon = retrieveTitleIcon()
            }

            Image(
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.CenterVertically),
                bitmap = romIcon?.bitmap?.asImageBitmap() ?: ImageBitmap(1, 1),
                contentDescription = null,
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
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.isDsiWareTitle) {
                        Image(
                            modifier = Modifier.width(50.dp),
                            contentScale = ContentScale.FillWidth,
                            painter = painterResource(id = R.drawable.logo_dsiware),
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.body1.copy(fontSize = 18.sp),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                    )
                }
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.body2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Divider()
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
            item = Rom("Highway 4: Mediocre Racing", "Highway_4.nds", Uri.EMPTY, Uri.EMPTY, RomConfig(), null, true),
            onClick = {},
            retrieveTitleIcon = { RomIcon(bitmap, RomIconFiltering.NONE) }
        )
    }
}