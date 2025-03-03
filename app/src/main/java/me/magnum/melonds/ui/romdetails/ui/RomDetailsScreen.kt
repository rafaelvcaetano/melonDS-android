package me.magnum.melonds.ui.romdetails.ui

import android.net.Uri
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.launch
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.domain.model.rom.config.RomConfig
import me.magnum.melonds.ui.common.MelonPreviewSet
import me.magnum.melonds.ui.romdetails.model.RomConfigUiModel
import me.magnum.melonds.ui.romdetails.model.RomConfigUiState
import me.magnum.melonds.ui.romdetails.model.RomConfigUpdateEvent
import me.magnum.melonds.ui.romdetails.model.RomDetailsTab
import me.magnum.melonds.ui.romdetails.model.RomRetroAchievementsUiState
import me.magnum.melonds.ui.theme.MelonTheme
import me.magnum.rcheevosapi.model.RAAchievement
import java.util.Date

@Composable
fun RomDetailsScreen(
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
    val systemUiController = rememberSystemUiController()
    val pagerState = rememberPagerState(
        initialPage = RomDetailsTab.CONFIG.tabIndex,
        pageCount = { RomDetailsTab.entries.size },
    )
    val coroutineScope = rememberCoroutineScope()

    systemUiController.isNavigationBarContrastEnforced = false

    Scaffold(
        topBar = {
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
        },
        backgroundColor = MaterialTheme.colors.surface,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        HorizontalPager(
            modifier = Modifier.fillMaxSize(),
            state = pagerState,
        ) {
            when (it) {
                RomDetailsTab.CONFIG.tabIndex -> {
                    RomConfigUi(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = padding,
                        romConfigUiState = romConfigUiState,
                        onConfigUpdate = onRomConfigUpdate,
                    )
                }
                RomDetailsTab.RETRO_ACHIEVEMENTS.tabIndex -> {
                    RomRetroAchievementsUi(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = padding,
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
        RomDetailsScreen(
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