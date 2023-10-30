package me.magnum.melonds.ui.dsiwaremanager.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
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
import me.magnum.melonds.ui.romlist.RomIcon
import me.magnum.melonds.ui.theme.MelonTheme

@Composable
fun DSiWareItem(
    modifier: Modifier,
    item: DSiWareTitle,
    onDeleteClicked: () -> Unit,
    retrieveTitleIcon: () -> RomIcon,
) {
    Column(modifier) {
        Row(Modifier.height(IntrinsicSize.Min).padding(start = 8.dp, top = 8.dp, bottom = 8.dp)) {
            val icon = remember(item.titleId) {
                retrieveTitleIcon()
            }

            Image(
                modifier = Modifier.size(48.dp).align(CenterVertically),
                bitmap = icon.bitmap?.asImageBitmap() ?: ImageBitmap(1, 1),
                contentDescription = null,
                filterQuality = when (icon.filtering) {
                    RomIconFiltering.NONE -> FilterQuality.None
                    RomIconFiltering.LINEAR -> DrawScope.DefaultFilterQuality
                },
            )
            Spacer(Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
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
            Icon(
                modifier = Modifier
                    .size(48.dp)
                    .align(CenterVertically)
                    .padding(8.dp)
                    .focusable()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDeleteClicked,
                        indication = rememberRipple(bounded = false),
                    ),
                painter = painterResource(id = R.drawable.ic_clear),
                contentDescription = "Delete",
                tint = MaterialTheme.colors.onSurface,
            )
        }
        Divider()
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
            item = DSiWareTitle("Highway 4: Mediocre Racing", "Playpark", 0, ByteArray(0)),
            onDeleteClicked = { },
            retrieveTitleIcon = { RomIcon(bitmap, RomIconFiltering.NONE) }
        )
    }
}