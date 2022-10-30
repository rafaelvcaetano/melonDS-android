package me.magnum.melonds.ui.dsiwaremanager

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.dsinand.ImportDSiWareTitleResult
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
                val importingTitle = viewModel.importingTitle.collectAsState(false)

                DSiWareManager(
                    modifier = Modifier.fillMaxSize(),
                    state = state.value,
                    onImportTitle = { viewModel.importTitleToNand(it) },
                    onDeleteTitle = { viewModel.deleteTitle(it) },
                    onBiosConfigurationFinished = { viewModel.revalidateBiosConfiguration() },
                    retrieveTitleIcon = { viewModel.getTitleIcon(it) },
                )

                if (importingTitle.value) {
                    Dialog(
                        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                        onDismissRequest = { },
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colors.secondary)
                    }
                }

                LaunchedEffect(null) {
                    viewModel.importTitleError.collectLatest {
                        Toast.makeText(this@DSiWareManagerActivity, getImportTitleResultMessage(it), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun getImportTitleResultMessage(result: ImportDSiWareTitleResult): String {
        return when (result) {
            ImportDSiWareTitleResult.SUCCESS -> ""
            ImportDSiWareTitleResult.NAND_NOT_OPEN -> getString(R.string.dsiware_manager_import_title_error_open_nand_failed)
            ImportDSiWareTitleResult.ERROR_OPENING_FILE -> getString(R.string.dsiware_manager_import_title_error_open_file_failed)
            ImportDSiWareTitleResult.NOT_DSIWARE_TITLE -> getString(R.string.dsiware_manager_import_title_error_not_dsiware_title)
            ImportDSiWareTitleResult.TITLE_ALREADY_IMPORTED -> getString(R.string.dsiware_manager_import_title_error_title_already_imported)
            ImportDSiWareTitleResult.INSATLL_FAILED -> getString(R.string.dsiware_manager_import_title_error_insatll_failed)
            ImportDSiWareTitleResult.UNKNOWN -> getString(R.string.dsiware_manager_import_title_error_unknown)
        }
    }
}