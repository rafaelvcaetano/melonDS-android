package me.magnum.melonds.ui.backgrounds

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.Surface
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.domain.model.Background
import me.magnum.melonds.ui.backgrounds.ui.BackgroundsScreen
import me.magnum.melonds.ui.theme.MelonTheme
import java.util.UUID

@AndroidEntryPoint
class BackgroundsActivity : AppCompatActivity() {
    companion object {
        const val KEY_INITIAL_BACKGROUND_ID = "initial_background_id"
        const val KEY_SELECTED_BACKGROUND_ID = "selected_background_id"
    }

    private val viewModel: BackgroundsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MelonTheme {
                Surface {
                    BackgroundsScreen(
                        viewModel = viewModel,
                        onBackgroundSelected = ::onBackgroundSelected,
                        onBackClick = ::finish,
                    )
                }
            }
        }
    }

    private fun onBackgroundSelected(background: Background?) {
        setSelectedBackgroundId(background?.id)
        finish()
    }

    private fun setSelectedBackgroundId(backgroundId: UUID?) {
        val intent = Intent().apply {
            putExtra(KEY_SELECTED_BACKGROUND_ID, backgroundId?.toString())
        }
        setResult(Activity.RESULT_OK, intent)
    }
}