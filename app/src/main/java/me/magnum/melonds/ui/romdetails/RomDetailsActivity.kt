package me.magnum.melonds.ui.romdetails

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
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
            val viewModel by viewModels<RomDetailsViewModel>()
            val rom by viewModel.rom.collectAsState()
            val romConfig by viewModel.romConfig.collectAsState()

            MelonTheme {
                systemUiController.setStatusBarColor(MaterialTheme.colors.surface)

                RomScreen(
                    modifier = Modifier.fillMaxSize(),
                    rom = rom,
                    romConfigUiState = romConfig,
                    loadRomIcon = { viewModel.getRomIcon(it) },
                    onLaunchRom = {
                        val intent = EmulatorActivity.getRomEmulatorActivityIntent(this, it)
                        startActivity(intent)
                    },
                    onNavigateBack = { onNavigateUp() },
                    onRomConfigUpdate = {
                        viewModel.onRomConfigUpdateEvent(it)
                    }
                )
            }
        }
    }
}