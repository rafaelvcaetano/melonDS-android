package me.magnum.melonds.ui.cheats

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.magnum.melonds.R
import me.magnum.melonds.ui.cheats.ui.CheatsScreen
import me.magnum.melonds.ui.theme.MelonTheme

@AndroidEntryPoint
class CheatsActivity : AppCompatActivity() {
    companion object {
        const val KEY_ROM_INFO = "key_rom_info"
    }

    private val viewModel: CheatsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        setContent {
            MelonTheme {
                CheatsScreen(viewModel = viewModel)
            }
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.cheatChangesCommittedEvent.collectLatest {
                    if (!it) {
                        Toast.makeText(this@CheatsActivity, R.string.failed_save_cheat_changes, Toast.LENGTH_LONG).show()
                    }
                    finish()
                }
            }
        }
    }
}