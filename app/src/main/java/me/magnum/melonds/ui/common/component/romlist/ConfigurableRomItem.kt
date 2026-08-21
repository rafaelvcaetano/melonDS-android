package me.magnum.melonds.ui.common.component.romlist

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component1
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component2
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.RomIconFiltering
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.domain.model.rom.config.RomConfig
import me.magnum.melonds.ui.common.MelonPreviewSet
import me.magnum.melonds.ui.romlist.RomIcon
import me.magnum.melonds.ui.theme.MelonTheme

@Composable
fun ConfigurableRomItem(
    modifier: Modifier,
    rom: Rom,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onConfigClick: () -> Unit,
    retrieveTitleIcon: suspend () -> RomIcon,
    contentPadding: PaddingValues = DefaultRomItemPadding,
) {
    val (mainFocusRequester, romDetailsFocusRequester) = remember { FocusRequester.createRefs() }
    CompositionLocalProvider(LocalContentAlpha provides if (enabled) ContentAlpha.high else ContentAlpha.disabled) {
        Column(modifier) {
            Row(
                modifier = Modifier.height(IntrinsicSize.Min).focusRequester(mainFocusRequester)
                    .focusProperties {
                        end = romDetailsFocusRequester
                    }
                    .clickable(enabled = enabled, onClick = onClick),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RomItemContent(
                    modifier = Modifier.weight(1f),
                    rom = rom,
                    enabled = enabled,
                    retrieveTitleIcon = retrieveTitleIcon,
                    contentPadding = contentPadding,
                )

                IconButton(
                    modifier = Modifier.size(48.dp)
                        .padding(8.dp)
                        .focusRequester(romDetailsFocusRequester)
                        .focusProperties {
                            start = mainFocusRequester
                        },
                    onClick = onConfigClick,
                    enabled = enabled,
                ) {
                    Icon(
                        modifier = Modifier.fillMaxSize(),
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.rom_settings),
                    )
                }
            }
            Divider()
        }
    }
}

@MelonPreviewSet
@Composable
private fun PreviewConfigurableRomItem() {
    val bitmap = createBitmap(1, 1).apply { this[0, 0] = 0xFF777777.toInt() }

    MelonTheme {
        ConfigurableRomItem(
            modifier = Modifier.fillMaxWidth(),
            rom = Rom("Highway 4: Mediocre Racing", "Nontendo", "Highway_4.nds", Uri.EMPTY, Uri.EMPTY, RomConfig(), null, false, ""),
            onClick = {},
            onConfigClick = {},
            retrieveTitleIcon = { RomIcon(bitmap, RomIconFiltering.NONE) }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewConfigurableRomItemDisabled() {
    val bitmap = createBitmap(1, 1).apply { this[0, 0] = 0xFF777777.toInt() }

    MelonTheme {
        ConfigurableRomItem(
            modifier = Modifier.fillMaxWidth(),
            rom = Rom("DSiWare Title", "Nontendo", "dsiware_title.nds", Uri.EMPTY, Uri.EMPTY, RomConfig(), null, true, ""),
            enabled = false,
            onClick = {},
            onConfigClick = {},
            retrieveTitleIcon = { RomIcon(bitmap, RomIconFiltering.NONE) }
        )
    }
}