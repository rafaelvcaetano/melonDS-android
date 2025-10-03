package me.magnum.melonds.ui.romdetails.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalElevationOverlay
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.pagerTabIndicatorOffset
import kotlinx.coroutines.launch
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.domain.model.rom.config.RomConfig
import me.magnum.melonds.ui.common.MelonPreviewSet
import me.magnum.melonds.ui.romdetails.model.RomDetailsTab
import me.magnum.melonds.ui.theme.MelonTheme
import java.util.Date

@OptIn(ExperimentalFoundationApi::class, ExperimentalPagerApi::class)
@Composable
fun RomHeaderUi(
    modifier: Modifier,
    rom: Rom,
    pagerState: PagerState,
    onLaunchRom: () -> Unit,
    onNavigateBack: () -> Unit,
    onTabClicked: (RomDetailsTab) -> Unit,
) {
    CompositionLocalProvider(
        LocalElevationOverlay provides null,
        LocalContentAlpha provides ContentAlpha.high,
    ) {
        Surface(
            modifier = modifier,
            elevation = 4.dp,
        ) {
            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.exclude(WindowInsets(bottom = Int.MAX_VALUE)))) {
                TopAppBar(
                    backgroundColor = MaterialTheme.colors.surface,
                    elevation = 0.dp,
                    title = {
                        if (LocalInspectionMode.current) {
                            Box(Modifier.size(42.dp).background(Color.Gray))
                        } else {
                            AsyncImage(
                                modifier = Modifier.size(42.dp),
                                model = rom,
                                contentDescription = null,
                                filterQuality = FilterQuality.None,
                            )
                        }

                        Text(
                            modifier = Modifier.padding(start = 16.dp),
                            text = rom.config.customName ?: rom.name,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                        }
                    },
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Button(
                        modifier = Modifier.widthIn(min = 132.dp),
                        shape = RoundedCornerShape(50),
                        onClick = onLaunchRom,
                        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.secondary, contentColor = MaterialTheme.colors.onSecondary),
                    ) {
                        Text(text = stringResource(R.string.play).uppercase())
                    }

                    val resources = LocalContext.current.resources
                    val romPlayTime = remember(rom.totalPlayTime) {
                        rom.totalPlayTime.toComponents { hours, minutes, _, _ ->
                            if (hours > 0) {
                                resources.getString(R.string.info_play_time_hours_minutes, hours, minutes)
                            } else {
                                resources.getString(R.string.info_play_time_minutes, minutes)
                            }
                        }
                    }
                    Text(
                        text = romPlayTime,
                        style = MaterialTheme.typography.body1,
                        maxLines = 1,
                    )
                }

                TabRow(
                    modifier = Modifier.fillMaxWidth(),
                    selectedTabIndex = pagerState.currentPage,
                    backgroundColor = MaterialTheme.colors.surface,
                    contentColor = MaterialTheme.colors.onSurface,
                    indicator = {
                        TabRowDefaults.Indicator(
                            modifier = Modifier.pagerTabIndicatorOffset(pagerState, it),
                            color = MaterialTheme.colors.secondary,
                        )
                    }
                ) {
                    Tab(
                        selected = pagerState.currentPage == RomDetailsTab.CONFIG.tabIndex,
                        onClick = { onTabClicked(RomDetailsTab.CONFIG) },
                        text = {
                            Text(
                                text = stringResource(id = R.string.rom_details_configuration_tab).uppercase(),
                                color = MaterialTheme.colors.onSurface,
                            )
                        }
                    )
                    Tab(
                        selected = pagerState.currentPage == RomDetailsTab.RETRO_ACHIEVEMENTS.tabIndex,
                        onClick = { onTabClicked(RomDetailsTab.RETRO_ACHIEVEMENTS) },
                        text = {
                            Text(
                                text = stringResource(id = R.string.retro_achievements_tab).uppercase(),
                                color = MaterialTheme.colors.onSurface
                            )
                        }
                    )
                }
            }
        }
    }
}

@ExperimentalFoundationApi
@MelonPreviewSet
@Composable
private fun PreviewRomHeaderUi() {
    MelonTheme {
        val pagerState = rememberPagerState(
            initialPage = RomDetailsTab.CONFIG.tabIndex,
            pageCount = { RomDetailsTab.entries.size },
        )
        val coroutineScope = rememberCoroutineScope()

        RomHeaderUi(
            modifier = Modifier.fillMaxWidth(),
            rom = Rom(
                name = "Professor Layton and the Lost Future",
                developerName = "Nontendo",
                fileName = "layton.nds",
                uri = Uri.EMPTY,
                parentTreeUri = Uri.EMPTY,
                config = RomConfig(customName = "Layton Custom"),
                lastPlayed = Date(),
                isDsiWareTitle = false,
                retroAchievementsHash = "",
            ),
            pagerState = pagerState,
            onLaunchRom = { },
            onNavigateBack = { }
        ) {
            coroutineScope.launch {
                pagerState.scrollToPage(it.tabIndex)
            }
        }
    }
}