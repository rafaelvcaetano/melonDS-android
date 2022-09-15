package me.magnum.melonds.ui.romlist

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import io.reactivex.disposables.Disposable
import me.magnum.melonds.R
import me.magnum.melonds.common.Permission
import me.magnum.melonds.databinding.DialogRomConfigBinding
import me.magnum.melonds.domain.model.Rom
import me.magnum.melonds.domain.model.RomConfig
import me.magnum.melonds.domain.model.RuntimeConsoleType
import me.magnum.melonds.domain.model.RuntimeMicSource
import me.magnum.melonds.extensions.setViewEnabledRecursive
import me.magnum.melonds.parcelables.RomConfigParcelable
import me.magnum.melonds.parcelables.RomParcelable
import me.magnum.melonds.ui.layouts.LayoutSelectorActivity
import me.magnum.melonds.common.contracts.FilePickerContract
import me.magnum.melonds.extensions.isMicrophonePermissionGranted
import me.magnum.melonds.utils.FileUtils
import java.util.*

class RomConfigDialog : DialogFragment() {
    companion object {
        private const val KEY_TITLE = "title"
        private const val KEY_ROM = "rom"
        private const val KEY_ROM_CONFIG = "rom_config"

        fun newInstance(title: String, rom: Rom): RomConfigDialog {
            return RomConfigDialog().apply {
                arguments = bundleOf(
                    KEY_TITLE to title,
                    KEY_ROM to RomParcelable(rom)
                )
            }
        }
    }

    private val romListViewModel: RomListViewModel by activityViewModels()
    private lateinit var binding: DialogRomConfigBinding
    private var layoutNameDisposable: Disposable? = null

    private lateinit var rom: Rom
    private lateinit var romConfig: RomConfig

    private val layoutPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val layoutId = result.data?.getStringExtra(LayoutSelectorActivity.KEY_SELECTED_LAYOUT_ID)?.let { UUID.fromString(it) }
            onLayoutIdSelected(layoutId)
        }
    }
    private val gbaRomFilePicker = registerForActivityResult(FilePickerContract(Permission.READ)) {
        if (it != null) {
            onGbaRomPathSelected(it)
        }
    }
    private val gbaSramFilePicker = registerForActivityResult(FilePickerContract(Permission.READ_WRITE)) {
        if (it != null) {
            onGbaSavePathSelected(it)
        }
    }
    private val microphonePermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            onRuntimeMicSourceSelected(RuntimeMicSource.DEVICE)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ROM is immutable so we can use the arguments' one. Only the ROM config needs to be saved if the fragment is rebuilt
        rom = arguments?.getParcelable<RomParcelable>(KEY_ROM)?.rom ?: return
        romConfig = if (savedInstanceState != null) {
            savedInstanceState.getParcelable<RomConfigParcelable>(KEY_ROM_CONFIG)?.romConfig ?: return
        } else {
            rom.config.copy()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogRomConfigBinding.inflate(layoutInflater)

        return AlertDialog.Builder(requireContext())
                .setView(binding.root)
                .setCancelable(true)
                .create()
    }

    override fun onStart() {
        super.onStart()
        isCancelable = true

        val title = arguments?.getString(KEY_TITLE)

        binding.layoutPrefSystem.setOnClickListener {
            AlertDialog.Builder(requireContext())
                    .setTitle(R.string.label_rom_config_console)
                    .setSingleChoiceItems(R.array.game_runtime_console_type_options, romConfig.runtimeConsoleType.ordinal) { dialog, which ->
                        val newConsoleType = RuntimeConsoleType.values()[which]
                        onRuntimeConsoleTypeSelected(newConsoleType)
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.cancel) { dialog, _ ->
                        dialog.cancel()
                    }
                    .show()
        }
        binding.layoutPrefRuntimeMicSource.setOnClickListener {
            AlertDialog.Builder(requireContext())
                    .setTitle(R.string.microphone_source)
                    .setSingleChoiceItems(R.array.game_runtime_mic_source_options, romConfig.runtimeMicSource.ordinal) { dialog, which ->
                        val newMicSource = RuntimeMicSource.values()[which]
                        // Request mic permission if required
                        if (newMicSource == RuntimeMicSource.DEVICE && !requireContext().isMicrophonePermissionGranted()) {
                            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            onRuntimeMicSourceSelected(newMicSource)
                        }

                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.cancel) { dialog, _ ->
                        dialog.cancel()
                    }
                    .show()
        }
        binding.layoutPrefLayout.setOnClickListener {
            val intent = Intent(requireContext(), LayoutSelectorActivity::class.java).apply {
                putExtra(LayoutSelectorActivity.KEY_SELECTED_LAYOUT_ID, romConfig.layoutId?.toString())
            }
            layoutPickerLauncher.launch(intent)
        }
        binding.layoutPrefLoadGbaRom.setOnClickListener { binding.switchLoadGbaRom.toggle() }
        binding.layoutPrefGbaRomPath.setOnClickListener {
            gbaRomFilePicker.launch(Pair(romConfig.gbaCartPath, null))
        }
        layoutNameDisposable = romListViewModel.getLayout(romConfig.layoutId).subscribe { layout ->
            binding.textPrefLayout.text = layout.name
        }
        binding.layoutPrefGbaSavePath.setOnClickListener {
            gbaSramFilePicker.launch(Pair(romConfig.gbaSavePath, null))
        }
        binding.switchLoadGbaRom.setOnCheckedChangeListener { _, isChecked -> setLoadGbaRom(isChecked) }
        binding.textRomConfigTitle.text = title

        onRuntimeConsoleTypeSelected(romConfig.runtimeConsoleType)
        onRuntimeMicSourceSelected(romConfig.runtimeMicSource)

        binding.switchLoadGbaRom.isChecked = romConfig.loadGbaCart
        binding.textPrefGbaRomPath.text = getUriPathOrDefault(romConfig.gbaCartPath)
        binding.textPrefGbaSavePath.text = getUriPathOrDefault(romConfig.gbaSavePath)

        binding.layoutPrefGbaRomPath.setViewEnabledRecursive(romConfig.loadGbaCart)
        binding.layoutPrefGbaSavePath.setViewEnabledRecursive(romConfig.loadGbaCart)

        binding.buttonRomConfigOk.setOnClickListener {
            romListViewModel.updateRomConfig(rom, romConfig)
            dismiss()
        }
        binding.buttonRomConfigCancel.setOnClickListener { dismiss() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(KEY_ROM_CONFIG, RomConfigParcelable(romConfig))
    }

    private fun setLoadGbaRom(loadGbaRom: Boolean) {
        romConfig.loadGbaCart = loadGbaRom
        binding.layoutPrefGbaRomPath.setViewEnabledRecursive(loadGbaRom)
        binding.layoutPrefGbaSavePath.setViewEnabledRecursive(loadGbaRom)
    }

    private fun onRuntimeConsoleTypeSelected(consoleType: RuntimeConsoleType) {
        val options = resources.getStringArray(R.array.game_runtime_console_type_options)
        romConfig.runtimeConsoleType = consoleType
        binding.textPrefRuntimeConsoleType.text = options[consoleType.ordinal]
    }

    private fun onRuntimeMicSourceSelected(micSource: RuntimeMicSource) {
        val options = resources.getStringArray(R.array.game_runtime_mic_source_options)
        romConfig.runtimeMicSource = micSource
        binding.textPrefRuntimeMicSource.text = options[micSource.ordinal]
    }

    private fun onLayoutIdSelected(layoutId: UUID?) {
        romConfig.layoutId = layoutId
        layoutNameDisposable?.dispose()
        layoutNameDisposable = romListViewModel.getLayout(layoutId).subscribe { layout ->
            binding.textPrefLayout.text = layout.name
        }
    }

    private fun onGbaRomPathSelected(romFileUri: Uri) {
        romConfig.gbaCartPath = romFileUri
        binding.textPrefGbaRomPath.text = getUriPathOrDefault(romFileUri)
    }

    private fun onGbaSavePathSelected(saveFileUri: Uri) {
        romConfig.gbaSavePath = saveFileUri
        binding.textPrefGbaSavePath.text = getUriPathOrDefault(saveFileUri)
    }

    private fun getUriPathOrDefault(uri: Uri?): String {
        return FileUtils.getAbsolutePathFromSAFUri(requireContext(), uri) ?: getString(R.string.not_set)
    }

    override fun onStop() {
        super.onStop()
        layoutNameDisposable?.dispose()
    }
}