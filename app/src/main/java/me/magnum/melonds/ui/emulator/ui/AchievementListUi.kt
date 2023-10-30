package me.magnum.melonds.ui.emulator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.retroachievements.RAUserAchievement
import me.magnum.melonds.ui.common.melonButtonColors
import me.magnum.melonds.ui.romdetails.model.RomRetroAchievementsUiState
import me.magnum.melonds.ui.romdetails.ui.RomAchievementUi
import me.magnum.rcheevosapi.model.RAAchievement

@Composable
fun AchievementListUi(
    modifier: Modifier,
    state: RomRetroAchievementsUiState,
    onViewAchievement: (RAAchievement) -> Unit,
    onRetry: () -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        when (state) {
            RomRetroAchievementsUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colors.secondary,
                )
            }
            is RomRetroAchievementsUiState.Ready -> {
                Content(
                    modifier = Modifier.widthIn(max = 640.dp)
                        .align(Alignment.Center),
                    achievements = state.achievements,
                    onViewAchievement = onViewAchievement,
                )
            }
            RomRetroAchievementsUiState.AchievementLoadError,
            RomRetroAchievementsUiState.LoggedOut,
            RomRetroAchievementsUiState.LoginError -> {
                LoadError(
                    modifier = Modifier.widthIn(max = 640.dp)
                        .padding(32.dp)
                        .align(Alignment.Center),
                    onRetry = onRetry,
                )
            }
        }
    }
}

@Composable
private fun Content(
    modifier: Modifier,
    achievements: List<RAUserAchievement>,
    onViewAchievement: (RAAchievement) -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .drawWithCache {
                val fadeHeight = 56 * density
                val topBrush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0f), Color.White),
                    endY = fadeHeight,
                )
                val bottomBrush = Brush.verticalGradient(
                    listOf(Color.White, Color.White.copy(alpha = 0f)),
                    startY = size.height - fadeHeight,
                    endY = size.height
                )

                onDrawWithContent {
                    drawContent()
                    drawRect(
                        brush = topBrush,
                        blendMode = BlendMode.DstIn,
                        size = Size(size.width, fadeHeight),
                    )
                    drawRect(
                        brush = bottomBrush,
                        blendMode = BlendMode.DstIn,
                        topLeft = Offset(0f, size.height - fadeHeight),
                        size = Size(size.width, fadeHeight),
                    )
                }
            },
        contentPadding = PaddingValues(vertical = 40.dp),
    ) {
        items(achievements) {
            RomAchievementUi(
                modifier = Modifier.fillMaxWidth(),
                userAchievement = it,
                onViewAchievement = { onViewAchievement(it.achievement) },
            )
        }
    }
}

@Composable
private fun LoadError(
    modifier: Modifier,
    onRetry: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(id = R.string.retro_achievements_load_error),
            textAlign = TextAlign.Center,
        )

        Button(
            onClick = onRetry,
            colors = melonButtonColors(),
        ) {
            Text(text = stringResource(id = R.string.retry).uppercase())
        }
    }
}