package me.magnum.melonds.ui.romlist.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.magnum.melonds.R
import me.magnum.melonds.common.Permission
import me.magnum.melonds.common.contracts.DirectoryPickerContract
import me.magnum.melonds.ui.common.melonButtonColors
import me.magnum.melonds.ui.theme.MelonTheme

private val DOCUMENT_PICKER_PACKAGES = listOf(
    "com.google.android.documentsui",
    "com.android.documentsui",
)

private sealed class FilePickerStatus {
    data object Available : FilePickerStatus()
    data class Disabled(val packageName: String) : FilePickerStatus()
    data object NotFound : FilePickerStatus()
}

@Composable
fun NoSearchDirectoriesContent(
    modifier: Modifier = Modifier,
    onDirectorySelected: (Uri) -> Unit,
) {
    val context = LocalContext.current

    val filePickerStatus = remember {
        val directoryPickerContract = DirectoryPickerContract(Permission.READ_WRITE)
        val directoryPickerIntent = directoryPickerContract.createIntent(context, null)
        val resolvedComponent = context.packageManager.resolveActivity(
            directoryPickerIntent,
            PackageManager.MATCH_DEFAULT_ONLY
        )

        if (resolvedComponent != null) {
            FilePickerStatus.Available
        } else {
            val disabledPicker = findDisabledFilePicker(context.packageManager)
            if (disabledPicker != null) {
                FilePickerStatus.Disabled(disabledPicker.packageName)
            } else {
                FilePickerStatus.NotFound
            }
        }
    }

    when (filePickerStatus) {
        is FilePickerStatus.Available -> {
            FilePickerAvailableContent(
                modifier = modifier,
                onDirectorySelected = onDirectorySelected,
            )
        }
        is FilePickerStatus.Disabled -> {
            FilePickerDisabledContent(
                modifier = modifier,
                disabledPackageName = filePickerStatus.packageName,
            )
        }
        is FilePickerStatus.NotFound -> {
            FilePickerNotFoundContent(modifier = modifier)
        }
    }
}

@Composable
private fun FilePickerAvailableContent(
    modifier: Modifier = Modifier,
    onDirectorySelected: (Uri) -> Unit,
) {
    val directoryPickerLauncher = rememberLauncherForActivityResult(DirectoryPickerContract(Permission.READ_WRITE)) { uri ->
        if (uri != null) {
            onDirectorySelected(uri)
        }
    }

    NoDirectoriesLayout(
        modifier = modifier,
        message = stringResource(R.string.no_rom_search_directory_specified),
        buttonText = stringResource(R.string.set_rom_directory),
        onButtonClick = { directoryPickerLauncher.launch(null) },
    )
}

@Composable
private fun FilePickerDisabledContent(
    modifier: Modifier = Modifier,
    disabledPackageName: String,
) {
    val context = LocalContext.current

    NoDirectoriesLayout(
        modifier = modifier,
        message = stringResource(R.string.system_file_picker_not_enabled),
        buttonText = stringResource(R.string.file_picker_settings),
        onButtonClick = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", disabledPackageName, null)
            }
            context.startActivity(intent)
        },
    )
}

@Composable
private fun FilePickerNotFoundContent(
    modifier: Modifier = Modifier,
) {
    NoDirectoriesLayout(
        modifier = modifier,
        message = stringResource(R.string.system_file_picker_not_found),
        buttonText = null,
        onButtonClick = { },
    )
}

@Composable
private fun NoDirectoriesLayout(
    modifier: Modifier = Modifier,
    message: String,
    buttonText: String?,
    onButtonClick: () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            Text(
                text = message,
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                color = MaterialTheme.colors.onSurface,
            )
            if (buttonText != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onButtonClick,
                    colors = melonButtonColors(),
                ) {
                    Text(text = buttonText)
                }
            }
        }
    }
}

private fun findDisabledFilePicker(packageManager: PackageManager): ApplicationInfo? {
    DOCUMENT_PICKER_PACKAGES.forEach { packageName ->
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            if (!appInfo.enabled) {
                return appInfo
            }
        } catch (_: PackageManager.NameNotFoundException) {
            // Ignore
        }
    }
    return null
}

@Preview(showBackground = true)
@Composable
private fun PreviewNoSearchDirectoriesAvailable() {
    MelonTheme {
        NoDirectoriesLayout(
            message = "No ROM search directory specified",
            buttonText = "Set ROM directory",
            onButtonClick = { },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewNoSearchDirectoriesNotFound() {
    MelonTheme {
        NoDirectoriesLayout(
            message = "Your device does not have a file picker. Without one, this emulator will not work",
            buttonText = null,
            onButtonClick = { },
        )
    }
}