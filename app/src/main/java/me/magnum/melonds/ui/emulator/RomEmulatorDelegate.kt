package me.magnum.melonds.ui.emulator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.Observer
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.*
import me.magnum.melonds.parcelables.RomInfoParcelable
import me.magnum.melonds.parcelables.RomParcelable
import me.magnum.melonds.ui.cheats.CheatsActivity
import me.magnum.melonds.utils.isMicrophonePermissionGranted
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

    private val cheatsLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        loadRomCheats(loadedRom).observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    MelonEmulator.setupCheats(it.toTypedArray())
                    activity.resumeEmulation()
                }
    }

    override fun getEmulatorSetupObservable(extras: Bundle?): Completable {
        val romParcelable = extras?.getParcelable(EmulatorActivity.KEY_ROM) as RomParcelable?

        val romLoader = if (romParcelable?.rom != null) {
            Single.just(romParcelable.rom)
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

        return romLoader.flatMap { rom ->
            activity.viewModel.loadLayoutForRom(rom)
            activity.viewModel.getRomLoader(rom)
        }.flatMap { romPair ->
            loadedRom = romPair.first
            return@flatMap loadRomCheats(loadedRom).defaultIfEmpty(emptyList()).flatMapSingle { cheats ->
                Single.create<MelonEmulator.LoadResult> { emitter ->
                    MelonEmulator.setupEmulator(getEmulatorConfigurationForRom(loadedRom), activity.assets, activity.buildUriFileHandler())

                    val rom = romPair.first
                    val romPath = romPair.second
                    val sramPath = activity.viewModel.getRomSramFile(rom)
                    val showBios = activity.settingsRepository.showBootScreen()

                    val gbaCartPath = rom.config.gbaCartPath
                    val gbaSavePath = rom.config.gbaSavePath
                    val loadResult = MelonEmulator.loadRom(romPath, sramPath, !showBios, rom.config.loadGbaCart(), gbaCartPath, gbaSavePath)
                    if (loadResult === MelonEmulator.LoadResult.NDS_FAILED)
                        throw EmulatorActivity.RomLoadFailedException(loadResult)

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
        val sramUri = activity.viewModel.getRomSramFile(loadedRom)
        return RomCrashContext(getEmulatorConfiguration(), activity.viewModel.getRomSearchDirectory()?.toString(), loadedRom.uri, sramUri)
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
        }.subscribeOn(AndroidSchedulers.mainThread()).observeOn(Schedulers.io())
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
        val romInfo = activity.viewModel.getRomInfo(loadedRom) ?: return

        val intent = Intent(activity, CheatsActivity::class.java)
        intent.putExtra(CheatsActivity.KEY_ROM_INFO, RomInfoParcelable.fromRomInfo(romInfo))
        cheatsLauncher.launch(intent)
    }

    private data class RomCrashContext(val emulatorConfiguration: EmulatorConfiguration, val romSearchDirUri: String?, val romUri: Uri, val sramUri: Uri)
}