package me.magnum.melonds.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import me.magnum.melonds.R
import me.magnum.melonds.databinding.DialogCheatsImportProgressBinding
import me.magnum.melonds.domain.model.CheatImportProgress

class CheatsImportProgressDialog : DialogFragment() {
    private val settingsViewModel: SettingsViewModel by activityViewModels()
    private lateinit var binding: DialogCheatsImportProgressBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogCheatsImportProgressBinding.inflate(LayoutInflater.from(requireContext()))

        return AlertDialog.Builder(requireContext())
                .setTitle(R.string.importing_cheats)
                .setView(binding.root)
                .setPositiveButton(R.string.move_to_background) { dialog, _ ->
                    dialog.dismiss()
                }
                .setCancelable(false)
                .create()
    }

    override fun onStart() {
        super.onStart()

        settingsViewModel.observeCheatsImportProgress().observe(this) {
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