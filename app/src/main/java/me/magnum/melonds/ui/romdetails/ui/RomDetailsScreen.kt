package me.magnum.melonds.ui.romdetails.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import me.magnum.melonds.domain.model.Rom
import me.magnum.melonds.domain.model.RomConfig
import me.magnum.melonds.ui.common.MelonPreviewSet
import me.magnum.melonds.ui.romdetails.model.*
import me.magnum.melonds.ui.theme.MelonTheme
import me.magnum.rcheevosapi.model.RAAchievement
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RomScreen(
    modifier: Modifier,
    rom: Rom,
    romConfigUiState: RomConfigUiState,
    retroAchievementsUiState: RomRetroAchievementsUiState,
    onNavigateBack: () -> Unit,
    onLaunchRom: (Rom) -> Unit,
    onRomConfigUpdate: (RomConfigUpdateEvent) -> Unit,
    onRetroAchievementsLogin: (username: String, password: String) -> Unit,
    onRetroAchievementsRetryLoad: () -> Unit,
    onViewAchievement: (RAAchievement) -> Unit,
) {
    val pagerState = rememberPagerState(RomDetailsTab.CONFIG.tabIndex)
    val coroutineScope = rememberCoroutineScope()

    Column(modifier) {
        RomHeaderUi(
            modifier = Modifier.fillMaxWidth(),
            rom = rom,
            pagerState = pagerState,
            onLaunchRom = { onLaunchRom(rom) },
            onNavigateBack = onNavigateBack
        ) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(it.tabIndex)
            }
        }
        HorizontalPager(
            modifier = Modifier.fillMaxWidth().weight(1f),
            pageCount = RomDetailsTab.values().size,
            state = pagerState,
        ) {
            when (it) {
                RomDetailsTab.CONFIG.tabIndex -> {
                    RomConfigUi(
                        modifier = Modifier.fillMaxSize(),
                        romConfigUiState = romConfigUiState,
                        onConfigUpdate = onRomConfigUpdate,
                    )
                }
                RomDetailsTab.RETRO_ACHIEVEMENTS.tabIndex -> {
                    RomRetroAchievementsUi(
                        modifier = Modifier.fillMaxSize(),
                        retroAchievementsUiState = retroAchievementsUiState,
                        onLogin = onRetroAchievementsLogin,
                        onRetryLoad = onRetroAchievementsRetryLoad,
                        onViewAchievement = onViewAchievement,
                    )
                }
            }
        }
    }
}

@MelonPreviewSet
@Composable
private fun PreviewRomScreen() {
    MelonTheme {
        RomScreen(
            modifier = Modifier.fillMaxSize(),
            rom = Rom(
                name = "Professor Layton and the Unwound Future",
                developerName = "Nontendo",
                fileName = "layton.nds",
                uri = Uri.EMPTY,
                parentTreeUri = Uri.EMPTY,
                config = RomConfig(),
                lastPlayed = Date(),
                isDsiWareTitle = false,
                retroAchievementsHash = "",
            ),
            romConfigUiState = RomConfigUiState.Ready(
                RomConfigUiModel(
                    layoutName = "Default",
                ),
            ),
            retroAchievementsUiState = RomRetroAchievementsUiState.LoggedOut,
            onNavigateBack = { },
            onLaunchRom = { },
            onRomConfigUpdate = { },
            onRetroAchievementsLogin = { _, _ -> },
            onRetroAchievementsRetryLoad = { },
            onViewAchievement = { },
        )
    }
}