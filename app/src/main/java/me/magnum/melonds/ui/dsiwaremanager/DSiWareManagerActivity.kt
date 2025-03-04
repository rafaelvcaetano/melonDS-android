package me.magnum.melonds.ui.dsiwaremanager

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.ui.dsiwaremanager.ui.DSiWareManagerScreen
import me.magnum.melonds.ui.theme.MelonTheme

@AndroidEntryPoint
class DSiWareManagerActivity : AppCompatActivity() {

    private val viewModel by viewModels<DSiWareManagerViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        setContent {
            MelonTheme {
                DSiWareManagerScreen(
                    viewModel = viewModel,
                    onBackClick = { onSupportNavigateUp() },
                )
            }
        }
    }
}