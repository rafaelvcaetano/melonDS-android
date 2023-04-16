package me.magnum.melonds.ui.emulator.rom

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.core.os.ConfigurationCompat
import com.squareup.picasso.Picasso
import io.reactivex.disposables.Disposable
import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.Rom
import me.magnum.melonds.domain.model.SaveStateSlot
import me.magnum.melonds.extensions.parcelable
import me.magnum.melonds.parcelables.RomParcelable
import me.magnum.melonds.ui.emulator.EmulatorActivity
import me.magnum.melonds.ui.emulator.EmulatorDelegate
import me.magnum.melonds.ui.emulator.PauseMenuOption
import java.text.SimpleDateFormat

class RomEmulatorDelegate(activity: EmulatorActivity, private val picasso: Picasso) : EmulatorDelegate(activity) {

    private lateinit var loadedRom: Rom
    private var cheatsLoadDisposable: Disposable? = null

    override fun getEmulatorSetupObservable(extras: Bundle?) {
        val romParcelable = extras?.parcelable(EmulatorActivity.KEY_ROM) as RomParcelable?

        if (romParcelable?.rom != null) {
            activity.viewModel.loadRom(romParcelable.rom)
        } else {
            if (extras?.containsKey(EmulatorActivity.KEY_PATH) == true) {
                val romPath = extras.getString(EmulatorActivity.KEY_PATH) ?: throw NullPointerException("${EmulatorActivity.KEY_PATH} was null")
                activity.viewModel.loadRom(romPath)
            } else if (extras?.containsKey(EmulatorActivity.KEY_URI) == true) {
                val romUri = extras.getString(EmulatorActivity.KEY_URI) ?: throw NullPointerException("${EmulatorActivity.KEY_URI} was null")
                activity.viewModel.loadRom(romUri.toUri())
            } else {
                throw NullPointerException("No ROM was specified")
            }
        }
    }

    override fun getPauseMenuOptions(): List<PauseMenuOption> {
        return activity.viewModel.getRomPauseMenuOptions()
    }

    override fun onPauseMenuOptionSelected(option: PauseMenuOption) {
        when (option) {
            RomPauseMenuOption.SETTINGS -> activity.openSettings()
            RomPauseMenuOption.SAVE_STATE -> pickSaveStateSlot {
                saveState(it)
                activity.resumeEmulation()
            }
            RomPauseMenuOption.LOAD_STATE -> pickSaveStateSlot {
                loadState(it)
                activity.resumeEmulation()
            }
            RomPauseMenuOption.REWIND -> activity.openRewindWindow()
            RomPauseMenuOption.CHEATS -> openCheatsActivity()
            RomPauseMenuOption.RESET -> activity.resetEmulation()
            RomPauseMenuOption.EXIT -> activity.finish()
        }
    }

    override fun performQuickSave() {
        MelonEmulator.pauseEmulation()
        val quickSlot = activity.viewModel.getRomQuickSaveStateSlot(loadedRom)
        if (saveState(quickSlot)) {
            Toast.makeText(activity, R.string.saved, Toast.LENGTH_SHORT).show()
        }
        MelonEmulator.resumeEmulation()
    }

    override fun performQuickLoad() {
        MelonEmulator.pauseEmulation()
        val quickSlot = activity.viewModel.getRomQuickSaveStateSlot(loadedRom)
        if (loadState(quickSlot)) {
            Toast.makeText(activity, R.string.loaded, Toast.LENGTH_SHORT).show()
        }
        MelonEmulator.resumeEmulation()
    }

    override fun dispose() {
        cheatsLoadDisposable?.dispose()
    }

    private fun saveState(slot: SaveStateSlot): Boolean {
        val saveStateUri = activity.viewModel.getRomSaveStateSlotUri(loadedRom, slot)
        return if (MelonEmulator.saveState(saveStateUri)) {
            val screenshot = activity.takeScreenshot()
            activity.viewModel.setRomSaveStateSlotScreenshot(loadedRom, slot, screenshot)
            true
        } else {
            Toast.makeText(activity, activity.getString(R.string.failed_save_state), Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun loadState(slot: SaveStateSlot): Boolean {
        return if (!slot.exists) {
            Toast.makeText(activity, activity.getString(R.string.cant_load_empty_slot), Toast.LENGTH_SHORT).show()
            false
        } else {
            val saveStateUri = activity.viewModel.getRomSaveStateSlotUri(loadedRom, slot)
            if (!MelonEmulator.loadState(saveStateUri)) {
                Toast.makeText(activity, activity.getString(R.string.failed_load_state), Toast.LENGTH_SHORT).show()
                false
            } else {
                true
            }
        }
    }

    private fun pickSaveStateSlot(onSlotPicked: (SaveStateSlot) -> Unit) {
        val dateFormatter = SimpleDateFormat("EEE, dd MMM yyyy", ConfigurationCompat.getLocales(activity.resources.configuration)[0])
        val timeFormatter = SimpleDateFormat("kk:mm:ss", ConfigurationCompat.getLocales(activity.resources.configuration)[0])
        val slots = activity.viewModel.getRomSaveStateSlots(loadedRom)
        var dialog: AlertDialog? = null
        var adapter: SaveStateListAdapter? = null

        adapter = SaveStateListAdapter(slots, picasso, dateFormatter, timeFormatter, {
            dialog?.cancel()
            onSlotPicked(it)
        }) {
            activity.viewModel.deleteRomSaveStateSlot(loadedRom, it)
            val newSlots = activity.viewModel.getRomSaveStateSlots(loadedRom)
            adapter?.updateSaveStateSlots(newSlots)
        }

        dialog = AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.save_slot))
                .setAdapter(adapter) { _, _ ->
                }
                .setNegativeButton(R.string.cancel) { dialogInterface, _ ->
                    dialogInterface.cancel()
                }
                .setOnCancelListener { activity.resumeEmulation() }
                .show()
    }

    private fun openCheatsActivity() {
        activity.openCheats(loadedRom) {
            activity.viewModel.onCheatsChanged()
            activity.resumeEmulation()
        }
    }
}