package me.magnum.melonds.ui.romdetails.ui

import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.PagerState
import com.google.accompanist.pager.pagerTabIndicatorOffset
import com.google.accompanist.pager.rememberPagerState
import kotlinx.coroutines.launch
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.Rom
import me.magnum.melonds.domain.model.RomConfig
import me.magnum.melonds.ui.common.MelonPreviewSet
import me.magnum.melonds.ui.romdetails.model.RomDetailsTab
import me.magnum.melonds.ui.theme.MelonTheme
import java.util.*

@OptIn(ExperimentalPagerApi::class)
@Composable
fun RomHeaderUi(
    modifier: Modifier,
    rom: Rom,
    pagerState: PagerState,
    onLaunchRom: () -> Unit,
    onNavigateBack: () -> Unit,
    onTabClicked: (RomDetailsTab) -> Unit,
) {
    CompositionLocalProvider(LocalElevationOverlay provides null) {
        Surface(
            modifier = modifier,
            elevation = 4.dp,
            color = MaterialTheme.colors.surface,
        ) {
            Column {
                TopAppBar(
                    backgroundColor = MaterialTheme.colors.surface,
                    elevation = 0.dp,
                    title = {
                        Text(
                            text = rom.name,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Filled.ArrowBack, null)
                        }
                    },
                    actions = {
                        Button(
                            modifier = Modifier.padding(8.dp),
                            shape = RoundedCornerShape(50),
                            onClick = onLaunchRom,
                            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.secondary, contentColor = MaterialTheme.colors.onSecondary),
                        ) {
                            Text(text = stringResource(R.string.play).uppercase())
                        }
                    }
                )

                TabRow(
                    modifier = Modifier.fillMaxWidth(),
                    selectedTabIndex = pagerState.currentPage,
                    backgroundColor = MaterialTheme.colors.surface,
                    contentColor = MaterialTheme.colors.primary,
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
                                color = MaterialTheme.colors.onBackground,
                            )
                        }
                    )
                    Tab(
                        selected = pagerState.currentPage == RomDetailsTab.RETRO_ACHIEVEMENTS.tabIndex,
                        onClick = { onTabClicked(RomDetailsTab.RETRO_ACHIEVEMENTS) },
                        text = {
                            Text(
                                text = stringResource(id = R.string.retro_achievements_tab).uppercase(),
                                color = MaterialTheme.colors.onBackground
                            )
                        }
                    )
                }
            }
        }
    }
}

@ExperimentalPagerApi
@MelonPreviewSet
@Composable
private fun PreviewRomHeaderUi() {
    MelonTheme {
        val pagerState = rememberPagerState(RomDetailsTab.CONFIG.tabIndex)
        val coroutineScope = rememberCoroutineScope()

        RomHeaderUi(
            modifier = Modifier.fillMaxWidth(),
            rom = Rom(
                name = "Professor Layton and the Lost Future",
                developerName = "Nontendo",
                fileName = "layton.nds",
                uri = Uri.EMPTY,
                parentTreeUri = Uri.EMPTY,
                config = RomConfig(),
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