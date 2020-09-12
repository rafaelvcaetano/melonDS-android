package me.magnum.melonds.ui.romlist

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import kotlinx.android.synthetic.main.dialog_rom_config.*
import me.magnum.melonds.R
import me.magnum.melonds.extensions.setViewEnabledRecursive
import me.magnum.melonds.model.RomConfig
import me.magnum.melonds.utils.FileUtils

class RomConfigDialog(context: Context, private val title: String, private val romConfig: RomConfig, private val filePicker: FilePicker) : AlertDialog(context) {
    interface OnRomConfigSavedListener {
        fun onRomConfigSaved(romConfig: RomConfig)
    }

    interface FilePicker {
        fun pickFile(startUri: Uri?, onFilePicked: (Uri) -> Unit)
    }

    private var saveListener: OnRomConfigSavedListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_rom_config)
        setCancelable(true)

        layoutPrefLoadGbaRom.setOnClickListener { switchLoadGbaRom.toggle() }
        layoutPrefGbaRomPath.setOnClickListener {
            filePicker.pickFile(romConfig.gbaCartPath, this::onGbaRomPathSelected)
        }
        layoutPrefGbaSavePath.setOnClickListener {
            filePicker.pickFile(romConfig.gbaSavePath, this::onGbaSavePathSelected)
        }
        switchLoadGbaRom.setOnCheckedChangeListener { _, isChecked -> setLoadGbaRom(isChecked) }
        textRomConfigTitle.text = title

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