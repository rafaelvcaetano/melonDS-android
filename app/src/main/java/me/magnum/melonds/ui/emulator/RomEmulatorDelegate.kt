package me.magnum.melonds.ui.emulator

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.Observer
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.*
import me.magnum.melonds.extensions.isMicrophonePermissionGranted
import me.magnum.melonds.parcelables.RomParcelable
import java.text.SimpleDateFormat

class RomEmulatorDelegate(activity: EmulatorActivity) : EmulatorDelegate(activity) {
    private enum class RomPauseMenuOptions(override val textResource: Int) : EmulatorActivity.PauseMenuOption {
        SETTINGS(R.string.settings),
        SAVE_STATE(R.string.save_state),
        LOAD_STATE(R.string.load_state),
        CHEATS(R.string.cheats),
        RESET(R.string.reset),
        EXIT(R.string.exit)
    }

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
                activity.viewModel.getRomAtUri(Uri.parse(romUri))
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
            return@flatMap loadRomCheats(loadedRom).defaultIfEmpty(emptyList()).flatMapSingle { cheats ->
                Single.create<MelonEmulator.LoadResult> { emitter ->
                    val emulatorConfiguration = getEmulatorConfigurationForRom(loadedRom)
                    MelonEmulator.setupEmulator(emulatorConfiguration, activity.assets, activity.buildUriFileHandler())

                    val rom = romPair.first
                    val romPath = romPair.second
                    val sramPath = activity.viewModel.getRomSramFile(rom)
                    val showBios = emulatorConfiguration.showBootScreen

                    val gbaCartPath = rom.config.gbaCartPath
                    val gbaSavePath = rom.config.gbaSavePath
                    val loadResult = MelonEmulator.loadRom(romPath, sramPath, !showBios, rom.config.loadGbaCart(), gbaCartPath, gbaSavePath)
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

    override fun getEmulatorConfiguration(): EmulatorConfiguration {
        return getEmulatorConfigurationForRom(loadedRom)
    }

    override fun getPauseMenuOptions(): List<EmulatorActivity.PauseMenuOption> {
        return RomPauseMenuOptions.values().toList()
    }

    override fun onPauseMenuOptionSelected(option: EmulatorActivity.PauseMenuOption) {
        when (option) {
            RomPauseMenuOptions.SETTINGS -> activity.openSettings()
            RomPauseMenuOptions.SAVE_STATE -> pickSaveStateSlot {
                val saveStateUri = activity.viewModel.getRomSaveStateSlotUri(loadedRom, it.slot)
                if (!MelonEmulator.saveState(saveStateUri))
                    Toast.makeText(activity, activity.getString(R.string.failed_save_state), Toast.LENGTH_SHORT).show()

                activity.resumeEmulation()
            }
            RomPauseMenuOptions.LOAD_STATE -> pickSaveStateSlot {
                if (!it.exists) {
                    Toast.makeText(activity, activity.getString(R.string.cant_load_empty_slot), Toast.LENGTH_SHORT).show()
                } else {
                    val saveStateUri = activity.viewModel.getRomSaveStateSlotUri(loadedRom, it.slot)
                    if (!MelonEmulator.loadState(saveStateUri))
                        Toast.makeText(activity, activity.getString(R.string.failed_load_state), Toast.LENGTH_SHORT).show()
                }

                activity.resumeEmulation()
            }
            RomPauseMenuOptions.CHEATS -> openCheatsActivity()
            RomPauseMenuOptions.RESET -> activity.resetEmulation()
            RomPauseMenuOptions.EXIT -> activity.finish()
        }
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

    private fun getEmulatorConfigurationForRom(rom: Rom): EmulatorConfiguration {
        val emulatorConfiguration = activity.viewModel.getEmulatorConfigurationForRom(rom)

        // Use BLOW mic source if mic permission is not granted
        return if (emulatorConfiguration.micSource == MicSource.DEVICE && !activity.isMicrophonePermissionGranted()) {
            emulatorConfiguration.copy(micSource = MicSource.BLOW)
        } else {
            emulatorConfiguration
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
        val dateFormatter = SimpleDateFormat("EEE, dd MMMM yyyy kk:mm:ss", ConfigurationCompat.getLocales(activity.resources.configuration)[0])
        val slots = activity.viewModel.getRomSaveStateSlots(loadedRom)
        val options = slots.map { "${it.slot}. ${if (it.exists) dateFormatter.format(it.lastUsedDate!!) else activity.getString(R.string.empty_slot)}" }.toTypedArray()

        AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.save_slot))
                .setItems(options) { _, which ->
                    onSlotPicked(slots[which])
                }
                .setNegativeButton(R.string.cancel) { dialog, _ ->
                    dialog.cancel()
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