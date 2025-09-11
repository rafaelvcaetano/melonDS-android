package me.magnum.melonds.ui.emulator.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.flow.collectLatest
import me.magnum.melonds.ui.emulator.EmulatorRetroAchievementsViewModel
import me.magnum.melonds.ui.theme.MelonTheme

@Composable
fun AchievementListDialog(
    viewModel: EmulatorRetroAchievementsViewModel,
    onDismiss: () -> Unit,
) {
    Dialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
    ) {
        (LocalView.current.parent as DialogWindowProvider).window.setDimAmount(0.8f)

        val context = LocalContext.current
        val achievementListState by viewModel.uiState.collectAsState()

        LaunchedEffect(Unit) {
            // Perform a load immediately so that the last achievement data is discarded. This is to ensure that the latest up-to-date data is displayed and
            // that if the user has loaded a new ROM, then the achievements of the new ROM are loaded
            viewModel.retryLoadAchievements()
            viewModel.viewAchievementEvent.collectLatest {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it))
                context.startActivity(intent)
            }
        }

        // Force dark colors here because the background will be dark
        MelonTheme(isDarkTheme = true) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colors.onSurface) {
                AchievementList(
                    modifier = Modifier.fillMaxSize(),
                    state = achievementListState,
                    onViewAchievement = viewModel::viewAchievement,
                    onRetry = viewModel::retryLoadAchievements,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}