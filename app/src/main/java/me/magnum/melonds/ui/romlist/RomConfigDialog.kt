package me.magnum.melonds.ui.romlist

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import kotlinx.android.synthetic.main.dialog_rom_config.*
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.RomConfig
import me.magnum.melonds.domain.model.RuntimeConsoleType
import me.magnum.melonds.domain.model.RuntimeMicSource
import me.magnum.melonds.extensions.setViewEnabledRecursive
import me.magnum.melonds.utils.FileUtils
import me.magnum.melonds.utils.isMicrophonePermissionGranted

class RomConfigDialog(context: Context, private val title: String, private val romConfig: RomConfig, private val romConfigDelegate: RomConfigDelegate) : AlertDialog(context) {
    interface OnRomConfigSavedListener {
        fun onRomConfigSaved(romConfig: RomConfig)
    }

    interface RomConfigDelegate {
        fun pickFile(startUri: Uri?, onFilePicked: (Uri) -> Unit)
        fun requestMicrophonePermission(onPermissionResult: (Boolean) -> Unit)
    }

    private var saveListener: OnRomConfigSavedListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_rom_config)
        setCancelable(true)

        layoutPrefSystem.setOnClickListener {
            AlertDialog.Builder(context)
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
        layoutPrefRuntimeMicSource.setOnClickListener {
            AlertDialog.Builder(context)
                    .setTitle(R.string.microphone_source)
                    .setSingleChoiceItems(R.array.game_runtime_mic_source_options, romConfig.runtimeMicSource.ordinal) { dialog, which ->
                        val newMicSource = RuntimeMicSource.values()[which]
                        // Request mic permission if required
                        if (newMicSource == RuntimeMicSource.DEVICE && !isMicrophonePermissionGranted(context)) {
                            romConfigDelegate.requestMicrophonePermission { granted ->
                                if (granted) {
                                    onRuntimeMicSourceSelected(newMicSource)
                                }
                            }
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
        layoutPrefLoadGbaRom.setOnClickListener { switchLoadGbaRom.toggle() }
        layoutPrefGbaRomPath.setOnClickListener {
            romConfigDelegate.pickFile(romConfig.gbaCartPath, this::onGbaRomPathSelected)
        }
        layoutPrefGbaSavePath.setOnClickListener {
            romConfigDelegate.pickFile(romConfig.gbaSavePath, this::onGbaSavePathSelected)
        }
        switchLoadGbaRom.setOnCheckedChangeListener { _, isChecked -> setLoadGbaRom(isChecked) }
        textRomConfigTitle.text = title

        onRuntimeConsoleTypeSelected(romConfig.runtimeConsoleType)
        if (romConfig.runtimeMicSource == RuntimeMicSource.DEVICE && !isMicrophonePermissionGranted(context)) {
            // Set mic source to BLOW if mic permission is not granted
            onRuntimeMicSourceSelected(RuntimeMicSource.BLOW)
        } else {
            onRuntimeMicSourceSelected(romConfig.runtimeMicSource)
        }
        switchLoadGbaRom.isChecked = romConfig.loadGbaCart()
        textPrefGbaRomPath.text = getUriPathOrDefault(romConfig.gbaCartPath)
        textPrefGbaSavePath.text = getUriPathOrDefault(romConfig.gbaSavePath)

        layoutPrefGbaRomPath.setViewEnabledRecursive(romConfig.loadGbaCart())
        layoutPrefGbaSavePath.setViewEnabledRecursive(romConfig.loadGbaCart())

        findViewById<View>(R.id.button_rom_config_ok)?.setOnClickListener {
            saveListener?.onRomConfigSaved(romConfig)
            dismiss()
        }
        findViewById<View>(R.id.button_rom_config_cancel)?.setOnClickListener { dismiss() }
    }

    fun setOnRomConfigSaveListener(listener: OnRomConfigSavedListener?): RomConfigDialog {
        saveListener = listener
        return this
    }

    private fun setLoadGbaRom(loadGbaRom: Boolean) {
        romConfig.setLoadGbaCart(loadGbaRom)
        layoutPrefGbaRomPath.setViewEnabledRecursive(loadGbaRom)
        layoutPrefGbaSavePath.setViewEnabledRecursive(loadGbaRom)
    }

    private fun onRuntimeConsoleTypeSelected(consoleType: RuntimeConsoleType) {
        val options = context.resources.getStringArray(R.array.game_runtime_console_type_options)
        romConfig.runtimeConsoleType = consoleType
        textPrefRuntimeConsoleType.text = options[consoleType.ordinal]
    }

    private fun onRuntimeMicSourceSelected(micSource: RuntimeMicSource) {
        val options = context.resources.getStringArray(R.array.game_runtime_mic_source_options)
        romConfig.runtimeMicSource = micSource
        textPrefRuntimeMicSource.text = options[micSource.ordinal]
    }

    private fun onGbaRomPathSelected(romFileUri: Uri) {
        romConfig.gbaCartPath = romFileUri
        textPrefGbaRomPath.text = getUriPathOrDefault(romFileUri)
    }

    private fun onGbaSavePathSelected(saveFileUri: Uri) {
        romConfig.gbaSavePath = saveFileUri
        textPrefGbaSavePath.text = getUriPathOrDefault(saveFileUri)
    }

    private fun getUriPathOrDefault(uri: Uri?): String {
        return FileUtils.getAbsolutePathFromSAFUri(context, uri) ?: context.getString(R.string.not_set)
    }
}