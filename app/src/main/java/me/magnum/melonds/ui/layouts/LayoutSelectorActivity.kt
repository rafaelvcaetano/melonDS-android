package me.magnum.melonds.ui.layouts

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.magnum.melonds.ui.layouts.model.SelectedLayout
import me.magnum.melonds.ui.layouts.ui.LayoutsScreen
import me.magnum.melonds.ui.layouts.viewmodel.LayoutSelectorViewModel
import me.magnum.melonds.ui.theme.MelonTheme

@AndroidEntryPoint
class LayoutSelectorActivity : AppCompatActivity() {
    companion object {
        const val KEY_SELECTED_LAYOUT_ID = "selected_layout_id"
    }

    private val viewModel by viewModels<LayoutSelectorViewModel>()

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

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedLayoutId.collect {
                    val intent = Intent().apply {
                        putExtra(KEY_SELECTED_LAYOUT_ID, it.layoutId?.toString())
                    }
                    setResult(RESULT_OK, intent)

                    if (it.reason == SelectedLayout.SelectionReason.SELECTED_BY_USER) {
                        finish()
                    }
                }
            }
        }
    }
}