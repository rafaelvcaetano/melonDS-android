package me.magnum.melonds.ui.dsiwaremanager

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.ui.dsiwaremanager.ui.DSiWareManager
import me.magnum.melonds.ui.theme.MelonTheme

@AndroidEntryPoint
class DSiWareManagerActivity : AppCompatActivity() {

    private val viewModel by viewModels<DSiWareManagerViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MelonTheme {
                val state = viewModel.state.collectAsState()
                DSiWareManager(
                    modifier = Modifier.fillMaxSize(),
                    state = state.value,
                    onImportTitle = { viewModel.importTitleToNand(it) },
                    onDeleteTitle = { viewModel.deleteTitle(it) },
                    retrieveTitleIcon = { viewModel.getTitleIcon(it) },
                )
            }
        }
    }
}