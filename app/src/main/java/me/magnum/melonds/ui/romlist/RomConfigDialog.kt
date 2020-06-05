package me.magnum.melonds.ui.romlist

import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.github.angads25.filepicker.model.DialogConfigs
import com.github.angads25.filepicker.model.DialogProperties
import com.github.angads25.filepicker.view.FilePickerDialog
import kotlinx.android.synthetic.main.dialog_rom_config.*
import me.magnum.melonds.R
import me.magnum.melonds.model.RomConfig
import me.magnum.melonds.utils.UIUtils
import java.io.File

class RomConfigDialog(context: Context, private val title: String, private val romConfig: RomConfig) : AlertDialog(context) {
    interface OnRomConfigSavedListener {
        fun onRomConfigSaved(romConfig: RomConfig)
    }

    private var saveListener: OnRomConfigSavedListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_rom_config)
        setCancelable(true)

        layoutPrefLoadGbaRom.setOnClickListener { switchLoadGbaRom.toggle() }
        layoutPrefGbaRomPath.setOnClickListener {
            val properties = DialogProperties()
            properties.selection_mode = DialogConfigs.SINGLE_MODE
            properties.selection_type = DialogConfigs.FILE_SELECT
            properties.root = Environment.getExternalStorageDirectory()
            val pickerDialog = FilePickerDialog(context, properties)
            pickerDialog.setDialogSelectionListener { files ->
                if (files.isNotEmpty()) {
                    if (File(files[0]).isFile) onGbaRomPathSelected(files[0])
                }
            }
            pickerDialog.show()
        }
        layoutPrefGbaSavePath.setOnClickListener {
            val properties = DialogProperties()
            properties.selection_mode = DialogConfigs.SINGLE_MODE
            properties.selection_type = DialogConfigs.FILE_SELECT
            properties.root = Environment.getExternalStorageDirectory()
            val pickerDialog = FilePickerDialog(context, properties)
            pickerDialog.setDialogSelectionListener { files ->
                if (files.isNotEmpty()) {
                    if (File(files[0]).isFile) onGbaSavePathSelected(files[0])
                }
            }
            pickerDialog.show()
        }
        switchLoadGbaRom.setOnCheckedChangeListener { _, isChecked -> setLoadGbaRom(isChecked) }
        textRomConfigTitle.text = title

        switchLoadGbaRom.isChecked = romConfig.loadGbaCart()
        textPrefGbaRomPath.text = getPathOrDefault(romConfig.gbaCartPath)
        textPrefGbaSavePath.text = getPathOrDefault(romConfig.gbaSavePath)

        UIUtils.setViewEnabled(layoutPrefGbaRomPath, romConfig.loadGbaCart())
        UIUtils.setViewEnabled(layoutPrefGbaSavePath, romConfig.loadGbaCart())

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
        UIUtils.setViewEnabled(layoutPrefGbaRomPath, loadGbaRom)
        UIUtils.setViewEnabled(layoutPrefGbaSavePath, loadGbaRom)
    }

    private fun onGbaRomPathSelected(romPath: String) {
        romConfig.gbaCartPath = romPath
        textPrefGbaRomPath.text = getPathOrDefault(romPath)
    }

    private fun onGbaSavePathSelected(savePath: String) {
        romConfig.gbaSavePath = savePath
        textPrefGbaSavePath.text = getPathOrDefault(savePath)
    }

    private fun getPathOrDefault(path: String?): String {
        return path ?: context.getString(R.string.not_set)
    }
}