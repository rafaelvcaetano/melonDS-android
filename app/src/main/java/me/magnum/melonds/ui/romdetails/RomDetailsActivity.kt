package me.magnum.melonds.ui.romdetails

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.ui.emulator.EmulatorActivity
import me.magnum.melonds.ui.romdetails.ui.RomDetailsScreen
import me.magnum.melonds.ui.theme.MelonTheme

@AndroidEntryPoint
class RomDetailsActivity : AppCompatActivity() {

    companion object {
        const val KEY_ROM = "rom"
    }

    private val romDetailsViewModel by viewModels<RomDetailsViewModel>()
    private val romRetroAchievementsViewModel by viewModels<RomDetailsRetroAchievementsViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            val rom by romDetailsViewModel.rom.collectAsState()
            val romConfig by romDetailsViewModel.romConfigUiState.collectAsState()

            val retroAchievementsUiState by romRetroAchievementsViewModel.uiState.collectAsState()

            LaunchedEffect(null) {
                romRetroAchievementsViewModel.viewAchievementEvent.collect {
                    launchViewAchievementIntent(it)
                }
            }

            MelonTheme {
                RomDetailsScreen(
                    rom = rom,
                    romConfigUiState = romConfig,
                    retroAchievementsUiState = retroAchievementsUiState,
                    onNavigateBack = { onNavigateUp() },
                    onLaunchRom = {
                        launchPlayRomIntent(it)
                    },
                    onRomConfigUpdate = {
                        romDetailsViewModel.onRomConfigUpdateEvent(it)
                    },
                    onRetroAchievementsLogin = { username, password ->
                        romRetroAchievementsViewModel.login(username, password)
                    },
                    onRetroAchievementsRetryLoad = {
                        romRetroAchievementsViewModel.retryLoadAchievements()
                    },
                    onViewAchievement = {
                        romRetroAchievementsViewModel.viewAchievement(it)
                    }
                )
            }
        }
    }

    private fun launchPlayRomIntent(rom: Rom) {
        val intent = EmulatorActivity.getRomEmulatorActivityIntent(this, rom)
        startActivity(intent)
    }

    private fun launchViewAchievementIntent(achievementUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(achievementUrl)
        }
        startActivity(intent)
    }
}