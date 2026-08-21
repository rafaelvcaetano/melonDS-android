package me.magnum.melonds.ui.romlist.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.core.net.toUri
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.RomIconFiltering
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.domain.model.rom.config.RomConfig
import me.magnum.melonds.ui.common.MelonPreviewSet
import me.magnum.melonds.ui.common.PaddedListBringIntoViewSpec
import me.magnum.melonds.ui.common.component.romlist.ConfigurableRomItem
import me.magnum.melonds.ui.common.component.romlist.RomItem
import me.magnum.melonds.ui.romlist.RomIcon
import me.magnum.melonds.ui.theme.MelonTheme

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun RomList(
    modifier: Modifier = Modifier,
    roms: List<Rom>?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    romItem: @Composable (Modifier, Rom) -> Unit,
) {
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = onRefresh,
    )

    Box(modifier.pullRefresh(pullRefreshState)) {
        when {
            roms == null -> {
                // This is just a temporary state while ROM data is not loaded
                Box(Modifier.fillMaxSize())
            }
            roms.isEmpty() && !isRefreshing -> {
                EmptyContent(Modifier.fillMaxSize().padding(contentPadding).consumeWindowInsets(contentPadding))
            }
            else -> {
                val density = LocalDensity.current
                val bringIntoViewSpec = remember(density) {
                    with(density) {
                        PaddedListBringIntoViewSpec(
                            leadingPadding = contentPadding.calculateTopPadding().toPx(),
                            trailingPadding = contentPadding.calculateBottomPadding().toPx(),
                        )
                    }
                }

                CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoViewSpec) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = contentPadding,
                    ) {
                        items(
                            items = roms,
                            key = { it.uri.toString() },
                        ) { rom ->
                            romItem(Modifier.animateItem(), rom)
                        }
                    }
                }
            }
        }

        PullRefreshIndicator(
            modifier = Modifier.align(Alignment.TopCenter),
            refreshing = isRefreshing,
            state = pullRefreshState,
            contentColor = MaterialTheme.colors.secondary,
        )
    }
}

@Composable
private fun EmptyContent(modifier: Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.no_roms_found),
            style = MaterialTheme.typography.body1,
        )
    }
}

@MelonPreviewSet
@Composable
private fun PreviewRomListWithRoms() {
    val bitmap = createBitmap(1, 1).apply { this[0, 0] = 0xFF777777.toInt() }

    MelonTheme {
        RomList(
            modifier = Modifier.fillMaxSize(),
            roms = listOf(
                Rom("Highway 4: Mediocre Racing", "Nontendo", "Highway_4.nds", "content://1".toUri(), Uri.EMPTY, RomConfig(), null, false, ""),
                Rom("Super Plumber Bros", "Nontendo", "plumber_bros.nds", "content://2".toUri(), Uri.EMPTY, RomConfig(), null, false, ""),
                Rom("DSiWare Title", "Someware", "dsiware.nds", "content://3".toUri(), Uri.EMPTY, RomConfig(), null, true, ""),
            ),
            isRefreshing = false,
            onRefresh = {},
        ) { modifier, rom ->
            ConfigurableRomItem(
                modifier = modifier.fillMaxWidth(),
                rom = rom,
                onClick = { },
                onConfigClick = { },
                retrieveTitleIcon = { RomIcon(bitmap, RomIconFiltering.NONE) },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewRomListEmpty() {
    MelonTheme {
        RomList(
            modifier = Modifier.fillMaxSize(),
            roms = emptyList(),
            isRefreshing = false,
            onRefresh = { },
        ) { modifier, rom ->
            RomItem(
                modifier = modifier.fillMaxWidth(),
                item = rom,
                onClick = { },
                retrieveTitleIcon = { RomIcon(null, RomIconFiltering.NONE) },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewRomListRefreshing() {
    MelonTheme {
        RomList(
            modifier = Modifier.fillMaxSize(),
            roms = emptyList(),
            isRefreshing = true,
            onRefresh = { },
        ) { modifier, rom ->
            RomItem(
                modifier = modifier.fillMaxWidth(),
                item = rom,
                onClick = { },
                retrieveTitleIcon = { RomIcon(null, RomIconFiltering.NONE) },
            )
        }
    }
}