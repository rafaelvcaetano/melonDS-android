package me.magnum.melonds.ui.romdetails

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.ui.emulator.EmulatorActivity
import me.magnum.melonds.ui.romdetails.ui.RomScreen
import me.magnum.melonds.ui.theme.MelonTheme

@AndroidEntryPoint
class RomDetailsActivity : AppCompatActivity() {

    companion object {
        const val KEY_ROM = "rom"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val systemUiController = rememberSystemUiController()
            val romDetailsViewModel by viewModels<RomDetailsViewModel>()
            val romRetroAchievementsViewModel by viewModels<RomDetailsRetroAchievementsViewModel>()

            val rom by romDetailsViewModel.rom.collectAsState()
            val romConfig by romDetailsViewModel.romConfig.collectAsState()

            val retroAchievementsUiState by romRetroAchievementsViewModel.uiState.collectAsState()

            LaunchedEffect(null) {
                romRetroAchievementsViewModel.viewAchievementEvent.collect {
                    launchViewAchievementIntent(it)
                }
            }

            MelonTheme {
                systemUiController.setStatusBarColor(MaterialTheme.colors.surface)

                Surface {
                    RomScreen(
                        modifier = Modifier.fillMaxSize(),
                        rom = rom,
                        romConfigUiState = romConfig,
                        retroAchievementsUiState = retroAchievementsUiState,
                        onNavigateBack = { onNavigateUp() },
                        onLaunchRom = {
                            val intent = EmulatorActivity.getRomEmulatorActivityIntent(this, it)
                            startActivity(intent)
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
    }

    private fun launchViewAchievementIntent(achievementUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(achievementUrl)
        }
        startActivity(intent)
    }
}