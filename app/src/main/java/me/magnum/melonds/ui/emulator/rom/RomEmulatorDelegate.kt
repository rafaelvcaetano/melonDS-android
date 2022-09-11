package me.magnum.melonds.ui.emulator.rom

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.Observer
import com.squareup.picasso.Picasso
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.*
import me.magnum.melonds.parcelables.RomParcelable
import me.magnum.melonds.ui.emulator.EmulatorActivity
import me.magnum.melonds.ui.emulator.EmulatorDelegate
import me.magnum.melonds.ui.emulator.PauseMenuOption
import java.text.SimpleDateFormat

class RomEmulatorDelegate(activity: EmulatorActivity, private val picasso: Picasso) : EmulatorDelegate(activity) {

    private lateinit var loadedRom: Rom
    private var cheatsLoadDisposable: Disposable? = null

    override fun getEmulatorSetupObservable(extras: Bundle?): Completable {
        val romParcelable = extras?.getParcelable(EmulatorActivity.KEY_ROM) as RomParcelable?

        val romLoader = if (romParcelable?.rom != null) {
            Maybe.just(romParcelable.rom)
        } else {
            if (extras?.containsKey(EmulatorActivity.KEY_PATH) == true) {
                val romPath = extras.getString(EmulatorActivity.KEY_PATH) ?: throw NullPointerException("${EmulatorActivity.KEY_PATH} was null")
                activity.viewModel.getRomAtPath(romPath)
            } else if (extras?.containsKey(EmulatorActivity.KEY_URI) == true) {
                val romUri = extras.getString(EmulatorActivity.KEY_URI) ?: throw NullPointerException("${EmulatorActivity.KEY_URI} was null")
                activity.viewModel.getRomAtUri(romUri.toUri())
            } else {
                throw NullPointerException("No ROM was specified")
            }
        }

        return romLoader.toSingle().onErrorResumeNext {
            if (it is NoSuchElementException) {
                showRomNotFoundDialog()
                // Prevent the observable from completing
                Single.never()
            } else {
                // Re-throw the error
                Single.error(it)
            }
        }.flatMap { rom ->
            activity.viewModel.loadLayoutForRom(rom)
            activity.viewModel.getRomLoader(rom)
        }.flatMap { romPair ->
            loadedRom = romPair.first

            loadRomCheats(loadedRom).toSingle(emptyList()).zipWith(getEmulatorLaunchConfiguration(loadedRom)) { cheats, emulatorConfiguration ->
                Pair(cheats, emulatorConfiguration)
            }.flatMap { (cheats, emulatorConfiguration) ->
                Single.create<MelonEmulator.LoadResult> { emitter ->
                    MelonEmulator.setupEmulator(emulatorConfiguration, activity.assets, activity.getRendererTextureBuffer())

                    val rom = romPair.first
                    val romPath = romPair.second
                    val sramPath = activity.viewModel.getRomSramFile(rom)

                    val gbaCartPath = rom.config.gbaCartPath
                    val gbaSavePath = rom.config.gbaSavePath
                    val loadResult = MelonEmulator.loadRom(romPath, sramPath, rom.config.mustLoadGbaCart(), gbaCartPath, gbaSavePath)
                    if (loadResult.isTerminal) {
                        throw EmulatorActivity.RomLoadFailedException(loadResult)
                    }

                    MelonEmulator.setupCheats(cheats.toTypedArray())
                    emitter.onSuccess(loadResult)
                }
            }
        }.doAfterSuccess {
            if (it == MelonEmulator.LoadResult.SUCCESS_GBA_FAILED) {
                activity.runOnUiThread {
                    Toast.makeText(activity, R.string.error_load_gba_rom, Toast.LENGTH_SHORT).show()
                }
            }
        }.ignoreElement()
    }

    private fun showRomNotFoundDialog() {
        activity.runOnUiThread {
            AlertDialog.Builder(activity)
                    .setTitle(R.string.error_rom_not_found)
                    .setMessage(R.string.error_rom_not_found_info)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        activity.finish()
                    }
                    .setOnDismissListener {
                        activity.finish()
                    }
                    .show()
        }
    }

    private fun getEmulatorConfigurationForRom(rom: Rom): EmulatorConfiguration {
        return activity.viewModel.getEmulatorConfigurationForRom(rom)
    }

    private fun getEmulatorLaunchConfiguration(rom: Rom): Single<EmulatorConfiguration> {
        val baseEmulatorConfiguration = getEmulatorConfigurationForRom(rom)
        return activity.adjustEmulatorConfigurationForPermissions(baseEmulatorConfiguration, true)
    }

    override fun getEmulatorConfiguration(): EmulatorConfiguration {
        val baseEmulatorConfiguration = getEmulatorConfigurationForRom(loadedRom)
        return activity.adjustEmulatorConfigurationForPermissions(baseEmulatorConfiguration, false).blockingGet()
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

    override fun getCrashContext(): Any {
        val sramUri = try {
            activity.viewModel.getRomSramFile(loadedRom)
        } catch (e: Exception) {
            null
        }
        return RomCrashContext(getEmulatorConfiguration(), activity.viewModel.getRomSearchDirectory()?.toString(), loadedRom.uri, sramUri)
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

    private fun loadRomCheats(rom: Rom): Maybe<List<Cheat>> {
        return Maybe.create<List<Cheat>> { emitter ->
            val romInfo = activity.viewModel.getRomInfo(rom)
            if (romInfo == null) {
                emitter.onComplete()
                return@create
            }

            val liveData = activity.viewModel.getRomEnabledCheats(romInfo)
            var observer: Observer<List<Cheat>>? = null
            observer = Observer {
                if (it == null) {
                    emitter.onComplete()
                } else {
                    emitter.onSuccess(it)
                }
                liveData.removeObserver(observer!!)
            }
            liveData.observeForever(observer)
        }.subscribeOn(activity.schedulers.uiThreadScheduler).observeOn(activity.schedulers.backgroundThreadScheduler)
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
            cheatsLoadDisposable?.dispose()
            cheatsLoadDisposable = loadRomCheats(loadedRom).observeOn(AndroidSchedulers.mainThread())
                    .subscribe {
                        MelonEmulator.setupCheats(it.toTypedArray())
                        activity.resumeEmulation()
                    }
        }
    }

    private data class RomCrashContext(val emulatorConfiguration: EmulatorConfiguration, val romSearchDirUri: String?, val romUri: Uri, val sramUri: Uri?)
}