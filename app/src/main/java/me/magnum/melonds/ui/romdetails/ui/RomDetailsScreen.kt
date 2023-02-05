package me.magnum.melonds.ui.romdetails.ui

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import kotlinx.coroutines.launch
import me.magnum.melonds.domain.model.Rom
import me.magnum.melonds.domain.model.RomConfig
import me.magnum.melonds.domain.model.RomIconFiltering
import me.magnum.melonds.ui.common.MelonPreviewSet
import me.magnum.melonds.ui.romdetails.model.*
import me.magnum.melonds.ui.romlist.RomIcon
import me.magnum.melonds.ui.theme.MelonTheme
import java.util.*

@OptIn(ExperimentalPagerApi::class)
@Composable
fun RomScreen(
    modifier: Modifier,
    rom: Rom,
    romConfigUiState: RomConfigUiState,
    retroAchievementsUiState: RomRetroAchievementsUiState,
    loadRomIcon: suspend (Rom) -> RomIcon,
    onNavigateBack: () -> Unit,
    onLaunchRom: (Rom) -> Unit,
    onRomConfigUpdate: (RomConfigUpdateEvent) -> Unit,
    onRetroAchievementsLogin: (username: String, password: String) -> Unit,
) {
    val pagerState = rememberPagerState(RomDetailsTab.CONFIG.tabIndex)
    val coroutineScope = rememberCoroutineScope()

    Column(modifier) {
        RomHeaderUi(
            modifier = Modifier.fillMaxWidth(),
            rom = rom,
            loadRomIcon = { loadRomIcon(rom) },
            pagerState = pagerState,
            onLaunchRom = { onLaunchRom(rom) },
            onNavigateBack = onNavigateBack,
            onTabClicked = {
                coroutineScope.launch {
                    pagerState.scrollToPage(it.tabIndex)
                }
            }
        )
        HorizontalPager(
            modifier = Modifier.fillMaxWidth().weight(1f),
            count = RomDetailsTab.values().size,
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
                fileName = "layton.nds",
                uri = Uri.EMPTY,
                parentTreeUri = Uri.EMPTY,
                config = RomConfig(),
                lastPlayed = Date(),
                isDsiWareTitle = false,
            ),
            romConfigUiState = RomConfigUiState.Ready(
                RomConfigUiModel(
                    layoutName = "Default",
                ),
            ),
            retroAchievementsUiState = RomRetroAchievementsUiState.LoggedOut,
            loadRomIcon = { RomIcon(null, RomIconFiltering.NONE) },
            onLaunchRom = { },
            onNavigateBack = { },
            onRomConfigUpdate = { },
            onRetroAchievementsLogin = { _, _ -> },
        )
    }
}