package me.magnum.melonds.ui.settings

import android.Manifest
import android.app.Dialog
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.magnum.melonds.R
import me.magnum.melonds.databinding.DialogCheatsImportProgressBinding
import me.magnum.melonds.domain.model.CheatImportProgress
import me.magnum.melonds.extensions.isNotificationPermissionGranted
import me.magnum.melonds.ui.settings.viewmodel.CheatsPreferencesViewModel

@AndroidEntryPoint
class CheatsImportProgressDialog : DialogFragment() {

    private val viewModel by viewModels<CheatsPreferencesViewModel>()
    private lateinit var binding: DialogCheatsImportProgressBinding

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogCheatsImportProgressBinding.inflate(layoutInflater)

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.importing_cheats)
            .setView(binding.root)
            .setPositiveButton(R.string.move_to_background) { dialog, _ ->
                requestNotificationPermission()
                dialog.dismiss()
            }
            .setCancelable(false)
            .create()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }

        if (!requireContext().isNotificationPermissionGranted()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.observeCheatsImportProgress().collect {
                    when (it.status) {
                        CheatImportProgress.CheatImportStatus.STARTING -> {
                            binding.progressBarCheatImport.isIndeterminate = true
                            binding.textCheatImportItemName.setText(R.string.starting)
                        }
                        CheatImportProgress.CheatImportStatus.ONGOING -> {
                            binding.progressBarCheatImport.isIndeterminate = false
                            binding.progressBarCheatImport.progress = (it.progress * 100).toInt()
                            binding.textCheatImportItemName.text = it.ongoingItemName
                        }
                        CheatImportProgress.CheatImportStatus.NOT_IMPORTING,
                        CheatImportProgress.CheatImportStatus.FAILED,
                        CheatImportProgress.CheatImportStatus.FINISHED -> dismiss()
                    }
                }
            }
        }
    }
}