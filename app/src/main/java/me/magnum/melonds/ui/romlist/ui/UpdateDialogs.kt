package me.magnum.melonds.ui.romlist.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jeziellago.compose.markdowntext.MarkdownText
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.DownloadProgress
import me.magnum.melonds.domain.model.Version
import me.magnum.melonds.domain.model.appupdate.AppUpdate
import me.magnum.melonds.ui.common.melonTextButtonColors
import me.magnum.melonds.ui.theme.MelonTheme

@Composable
fun ProdUpdateAvailableDialog(
    update: AppUpdate,
    onUpdate: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    val versionString = getReadableVersionString(update.newVersion)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.update_available, versionString),
                style = MaterialTheme.typography.h6,
            )
        },
        text = {
            MarkdownText(
                markdown = update.description,
                style = MaterialTheme.typography.body2,
            )
        },
        buttons = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                ) {
                    TextButton(
                        onClick = onSkip,
                        colors = melonTextButtonColors(),
                    ) {
                        Text(stringResource(R.string.skip_update).uppercase())
                    }
                    TextButton(
                        onClick = onDismiss,
                        colors = melonTextButtonColors(),
                    ) {
                        Text(stringResource(R.string.cancel).uppercase())
                    }
                    TextButton(
                        onClick = onUpdate,
                        colors = melonTextButtonColors(),
                    ) {
                        Text(stringResource(R.string.update).uppercase())
                    }
                }
            }
        },
    )
}

@Composable
fun NightlyUpdateDialog(
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.nightly_update_available),
                style = MaterialTheme.typography.h6,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.nightly_update_available_message),
                style = MaterialTheme.typography.body2,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onUpdate,
                colors = melonTextButtonColors(),
            ) {
                Text(stringResource(R.string.update).uppercase())
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = melonTextButtonColors(),
            ) {
                Text(stringResource(R.string.remind_later_update).uppercase())
            }
        },
    )
}

@Composable
fun DownloadProgressDialog(
    downloadProgress: DownloadProgress.DownloadUpdate,
    onMoveToBackground: () -> Unit,
) {
    val progress = (downloadProgress.downloadedBytes.toDouble() / downloadProgress.totalSize).toFloat()
    val downloadedMb = downloadProgress.downloadedBytes.toDouble() / 1024 / 1024
    val totalMb = downloadProgress.totalSize.toDouble() / 1024 / 1024

    AlertDialog(
        onDismissRequest = { /* Non-dismissable */ },
        title = {
            Text(
                text = stringResource(R.string.downloading_update),
                style = MaterialTheme.typography.h6,
            )
        },
        text = {
            Column {
                if (downloadProgress.totalSize > 0) {
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colors.primary,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colors.primary,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.download_progress_sizes, downloadedMb, totalMb),
                    style = MaterialTheme.typography.body2,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onMoveToBackground,
                colors = melonTextButtonColors(),
            ) {
                Text(stringResource(R.string.move_to_background).uppercase())
            }
        },
    )
}

@Composable
private fun getReadableVersionString(version: Version): String {
    val typeString = when (version.type) {
        Version.ReleaseType.ALPHA -> stringResource(R.string.version_alpha)
        Version.ReleaseType.BETA -> stringResource(R.string.version_beta)
        Version.ReleaseType.FINAL -> ""
        Version.ReleaseType.NIGHTLY -> return stringResource(R.string.version_nightly)
    }
    return "$typeString${if (typeString.isEmpty()) "" else " "}${version.major}.${version.minor}.${version.patch}"
}

@Preview
@Composable
private fun PreviewNightlyUpdateDialog() {
    MelonTheme {
        NightlyUpdateDialog(
            onUpdate = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun PreviewDownloadProgressDialog() {
    MelonTheme {
        DownloadProgressDialog(
            downloadProgress = DownloadProgress.DownloadUpdate(
                totalSize = 50_000_000L,
                downloadedBytes = 25_000_000L,
            ),
            onMoveToBackground = {},
        )
    }
}
