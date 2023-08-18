package me.magnum.melonds.ui.emulator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.magnum.melonds.R
import me.magnum.melonds.ui.emulator.model.RAIntegrationEvent

@Composable
fun RAIntegrationEventUi(modifier: Modifier, event: RAIntegrationEvent) {
    Card(
        modifier = modifier
            .padding(16.dp)
            .shadow(8.dp, RoundedCornerShape(8.dp))
            .widthIn(max = 400.dp),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            event.icon?.let {
                AsyncImage(
                    modifier = Modifier.size(40.dp),
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(it.toString())
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                )
            }

            when (event) {
                is RAIntegrationEvent.Failed -> {
                    Column {
                        Text(
                            text = stringResource(id = R.string.achievements_failed_load),
                            style = MaterialTheme.typography.body2,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colors.error,
                            maxLines = 1,
                        )
                        Text(
                            text = stringResource(id = R.string.achievements_failed_load_tip),
                            style = MaterialTheme.typography.body2,
                            maxLines = 1,
                        )
                    }
                }
                is RAIntegrationEvent.Loaded -> {
                    Column {
                        Text(
                            text = stringResource(id = R.string.achievements_loaded),
                            style = MaterialTheme.typography.body2,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Text(
                            text = stringResource(id = R.string.achievements_unlocked_compact, event.unlockedAchievements, event.totalAchievements),
                            style = MaterialTheme.typography.body2,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}