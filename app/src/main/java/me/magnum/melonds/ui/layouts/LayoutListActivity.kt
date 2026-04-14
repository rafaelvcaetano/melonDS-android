package me.magnum.melonds.ui.layouts

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.ui.layouts.ui.LayoutsScreen
import me.magnum.melonds.ui.layouts.viewmodel.LayoutsViewModel
import me.magnum.melonds.ui.theme.MelonTheme

@AndroidEntryPoint
class LayoutListActivity : AppCompatActivity() {

    private val viewModel: LayoutsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            MelonTheme {
                LayoutsScreen(
                    viewModel = viewModel,
                    onNavigateBack = ::finish,
                )
            }
        }
    }
}