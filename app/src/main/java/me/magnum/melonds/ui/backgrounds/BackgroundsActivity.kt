package me.magnum.melonds.ui.backgrounds

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.domain.model.Background
import me.magnum.melonds.parcelables.BackgroundParcelable
import me.magnum.melonds.ui.backgrounds.ui.BackgroundListScreen
import me.magnum.melonds.ui.backgrounds.ui.BackgroundPreviewScreen
import me.magnum.melonds.ui.theme.MelonTheme
import java.util.UUID

@OptIn(ExperimentalSharedTransitionApi::class)
@AndroidEntryPoint
class BackgroundsActivity : AppCompatActivity() {
    companion object {
        const val KEY_INITIAL_BACKGROUND_ID = "initial_background_id"
        const val KEY_SELECTED_BACKGROUND_ID = "selected_background_id"
    }

    private val viewModel: BackgroundsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        setContent {
            MelonTheme {
                val navController = rememberNavController()

                SharedTransitionLayout {
                    NavHost(
                        navController = navController,
                        startDestination = BackgroundsNavigation.BackgroundList,
                        popEnterTransition = { EnterTransition.None },
                        enterTransition = { fadeIn() },
                        popExitTransition = { fadeOut() },
                    ) {
                        composable<BackgroundsNavigation.BackgroundList> {
                            BackgroundListScreen(
                                viewModel = viewModel,
                                onBackgroundSelected = ::onBackgroundSelected,
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedContentScope = this@composable,
                                onPreviewBackgroundClick = {
                                    navController.navigate(BackgroundsNavigation.BackgroundPreview(BackgroundParcelable.fromBackground(it)))
                                },
                                onBackClick = { finish() },
                            )
                        }
                        composable<BackgroundsNavigation.BackgroundPreview>(typeMap = BackgroundsNavigation.BackgroundPreview.typeMap) {
                            val route = it.toRoute<BackgroundsNavigation.BackgroundPreview>()
                            BackgroundPreviewScreen(
                                background = route.backgroundParcelable.toBackground(),
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedContentScope = this@composable,
                                onBackClick = { navController.navigateUp() },
                            )
                        }
                    }
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