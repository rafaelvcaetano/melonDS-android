package me.magnum.melonds.ui.backgrounds.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarResult
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.launch
import me.magnum.melonds.R
import me.magnum.melonds.common.Permission
import me.magnum.melonds.common.contracts.FilePickerContract
import me.magnum.melonds.common.contracts.PreviewBackgroundContract
import me.magnum.melonds.domain.model.Background
import me.magnum.melonds.extensions.nameWithoutExtension
import me.magnum.melonds.ui.backgrounds.BackgroundsViewModel
import me.magnum.melonds.ui.common.component.dialog.TextInputDialog
import me.magnum.melonds.ui.common.component.dialog.rememberTextInputDialogState
import me.magnum.melonds.utils.BitmapRegionDecoderCompat
import java.util.UUID

@Composable
fun BackgroundsScreen(
    viewModel: BackgroundsViewModel,
    onBackgroundSelected: (Background?) -> Unit,
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    val textInputDialogState = rememberTextInputDialogState()
    val backgrounds by viewModel.backgrounds.collectAsStateWithLifecycle()
    val selectedBackgroundId by viewModel.currentSelectedBackground.collectAsStateWithLifecycle()

    val addBackgroundLauncher = rememberLauncherForActivityResult(FilePickerContract(Permission.READ)) { uri ->
        uri?.let {
            if (!isBackgroundValid(context, uri)) {
                Toast.makeText(context, R.string.background_add_processing_failed, Toast.LENGTH_LONG).show()
                return@let
            }

            val documentName = DocumentFile.fromSingleUri(context, it)?.nameWithoutExtension ?: ""
            textInputDialogState.show(
                initialText = documentName,
                onConfirm = { backgroundName ->
                    val newBackground = Background(null, backgroundName, uri)
                    viewModel.addBackground(newBackground)
                }
            )
        }
    }
    val backgroundPreviewLauncher = rememberLauncherForActivityResult(PreviewBackgroundContract()) {
        /* no-op */
    }
    val backgroundDeletedMessage = stringResource(R.string.background_deleted)
    val undoMessage = stringResource(R.string.undo)
    val scaffoldState = rememberScaffoldState()
    val coroutineScope = rememberCoroutineScope()
    val systemUiController = rememberSystemUiController()

    systemUiController.setStatusBarColor(MaterialTheme.colors.primaryVariant)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        scaffoldState = scaffoldState,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backgrounds)) },
                backgroundColor = MaterialTheme.colors.primary,
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack),
                            contentDescription = null,
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    addBackgroundLauncher.launch(Pair(null, arrayOf("image/png", "image/jpeg")))
                },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.action_backgrounds_new),
                )
            }
        }
    ) { padding ->
        if (backgrounds == null) {
            Loading(Modifier
                .padding(padding)
                .fillMaxSize())
        } else {
            BackgroundList(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                backgrounds = backgrounds.orEmpty(),
                selectedBackgroundId = selectedBackgroundId,
                onBackgroundClick = {
                    viewModel.selectBackground(it)
                    onBackgroundSelected(it)
                },
                onPreviewBackgroundClick = {
                    backgroundPreviewLauncher.launch(it)
                },
                onDeleteBackgroundClick = {
                    viewModel.deleteBackground(it)
                    coroutineScope.launch {
                        val result = scaffoldState.snackbarHostState.showSnackbar(
                            message = backgroundDeletedMessage,
                            actionLabel = undoMessage,
                            duration = SnackbarDuration.Long,
                        )

                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.addBackground(it)
                        }
                    }
                },
            )
        }
    }

    TextInputDialog(
        title = stringResource(R.string.background_name),
        dialogState = textInputDialogState,
    )
}

@Composable
private fun Loading(modifier: Modifier) {
    Box(modifier) {
        CircularProgressIndicator(
            modifier = Modifier.align(Alignment.Center),
            color = MaterialTheme.colors.secondary,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackgroundList(
    modifier: Modifier,
    backgrounds: List<Background?>,
    selectedBackgroundId: UUID?,
    onBackgroundClick: (Background?) -> Unit,
    onPreviewBackgroundClick: (Background) -> Unit,
    onDeleteBackgroundClick: (Background) -> Unit,
) {
    FlowRow(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        backgrounds.forEach {
            if (it == null) {
                NoneBackgroundItem(
                    isSelected = selectedBackgroundId == null,
                    onClick = { onBackgroundClick(null) },
                )
            } else {
                BackgroundItem(
                    background = it,
                    isSelected = selectedBackgroundId == it.id,
                    onClick = { onBackgroundClick(it) },
                    onPreviewClick = { onPreviewBackgroundClick(it) },
                    onDeleteClick = { onDeleteBackgroundClick(it) },
                )
            }
        }
    }
}

private fun isBackgroundValid(context: Context, backgroundUri: Uri): Boolean {
    return runCatching {
        context.contentResolver.openInputStream(backgroundUri)?.use {
            BitmapRegionDecoderCompat.newInstance(it)?.recycle()
        } ?: throw Exception("Failed to open stream")
    }.isSuccess
}