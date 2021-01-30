package me.magnum.melonds.ui.emulator

import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.ConfigurationCompat
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.Completable
import io.reactivex.Single
import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.EmulatorConfiguration
import me.magnum.melonds.domain.model.MicSource
import me.magnum.melonds.domain.model.Rom
import me.magnum.melonds.domain.model.SaveStateSlot
import me.magnum.melonds.parcelables.RomParcelable
import me.magnum.melonds.utils.FileUtils
import me.magnum.melonds.utils.isMicrophonePermissionGranted
import java.io.File
import java.text.SimpleDateFormat

@AndroidEntryPoint
class RomEmulatorActivity : EmulatorActivity() {
    companion object {
        const val KEY_ROM = "rom"
        const val KEY_PATH = "PATH"
    }

    private enum class RomPauseMenuOptions(override val textResource: Int) : PauseMenuOption {
        SETTINGS(R.string.settings),
        SAVE_STATE(R.string.save_state),
        LOAD_STATE(R.string.load_state),
        EXIT(R.string.exit)
    }

    private lateinit var loadedRom: Rom

    override fun getEmulatorSetupObservable(): Completable {
        val romParcelable = intent.extras?.getParcelable(KEY_ROM) as RomParcelable?

        val romLoader = if (romParcelable?.rom != null)
            Single.just(romParcelable.rom)
        else {
            val romPath = intent.extras?.getString(KEY_PATH) ?: throw NullPointerException("No ROM was specified")
            viewModel.getRomAtPath(romPath)
        }

        return romLoader.flatMap { rom ->
            loadedRom = rom
            return@flatMap Single.create<MelonEmulator.LoadResult> { emitter ->
                MelonEmulator.setupEmulator(getEmulatorConfigurationForRom(rom), assets)

                val romPath = FileUtils.getAbsolutePathFromSAFUri(this, rom.uri) ?: throw RomLoadFailedException()
                val showBios = settingsRepository.showBootScreen()
                val sramPath = getSRAMPath(rom.uri)

                val gbaCartPath = FileUtils.getAbsolutePathFromSAFUri(this, rom.config.gbaCartPath)
                val gbaSavePath = FileUtils.getAbsolutePathFromSAFUri(this, rom.config.gbaSavePath)
                val loadResult = MelonEmulator.loadRom(romPath, sramPath, !showBios, rom.config.loadGbaCart(), gbaCartPath, gbaSavePath)
                if (loadResult === MelonEmulator.LoadResult.NDS_FAILED)
                    throw RomLoadFailedException()

                emitter.onSuccess(loadResult)
            }
        }.doAfterSuccess {
            if (it == MelonEmulator.LoadResult.SUCCESS_GBA_FAILED) {
                Toast.makeText(this, R.string.error_load_gba_rom, Toast.LENGTH_SHORT).show()
            }
        }.ignoreElement()
    }

    override fun getEmulatorConfiguration(): EmulatorConfiguration {
        return getEmulatorConfigurationForRom(loadedRom)
    }

    override fun getPauseMenuOptions(): List<PauseMenuOption> {
        return RomPauseMenuOptions.values().toList()
    }

    override fun onPauseMenuOptionSelected(option: PauseMenuOption) {
        when (option) {
            RomPauseMenuOptions.SETTINGS -> openSettings()
            RomPauseMenuOptions.SAVE_STATE -> pickSaveStateSlot {
                if (!MelonEmulator.saveState(it.path))
                    Toast.makeText(this, getString(R.string.failed_save_state), Toast.LENGTH_SHORT).show()

                resumeEmulation()
            }
            RomPauseMenuOptions.LOAD_STATE -> pickSaveStateSlot {
                if (!it.exists) {
                    Toast.makeText(this, getString(R.string.cant_load_empty_slot), Toast.LENGTH_SHORT).show()
                } else {
                    if (!MelonEmulator.loadState(it.path))
                        Toast.makeText(this, getString(R.string.failed_load_state), Toast.LENGTH_SHORT).show()
                }

                resumeEmulation()
            }
            RomPauseMenuOptions.EXIT -> finish()
        }
    }

    private fun getEmulatorConfigurationForRom(rom: Rom): EmulatorConfiguration {
        val emulatorConfiguration = viewModel.getEmulatorConfigurationForRom(rom)

        // Use BLOW mic source if mic permission is not granted
        return if (emulatorConfiguration.micSource == MicSource.DEVICE && !isMicrophonePermissionGranted(this)) {
            emulatorConfiguration.copy(micSource = MicSource.BLOW)
        } else {
            emulatorConfiguration
        }
    }

    private fun getSRAMPath(romUri: Uri): String {
        val romPath = FileUtils.getAbsolutePathFromSAFUri(this, romUri)
        val romFile = File(romPath!!)

        val sramDir = if (settingsRepository.saveNextToRomFile()) {
            romFile.parent
        } else {
            val sramDirUri = settingsRepository.getSaveFileDirectory()
            if (sramDirUri != null)
                FileUtils.getAbsolutePathFromSAFUri(this, sramDirUri) ?: romFile.parent
            else {
                // If no directory is set, revert to using the ROM's directory
                romFile.parent
            }
        }

        val nameWithoutExtension = romFile.nameWithoutExtension
        val sramFileName = "$nameWithoutExtension.sav"
        return File(sramDir, sramFileName).absolutePath
    }

    private fun pickSaveStateSlot(onSlotPicked: (SaveStateSlot) -> Unit) {
        val dateFormatter = SimpleDateFormat("EEE, dd MMMM yyyy kk:mm:ss", ConfigurationCompat.getLocales(resources.configuration)[0])
        val slots = viewModel.getRomSaveStateSlots(loadedRom)
        val options = slots.map { "${it.slot}. ${if (it.exists) dateFormatter.format(it.lastUsedDate!!) else getString(R.string.empty_slot)}" }.toTypedArray()

        AlertDialog.Builder(this)
                .setTitle(getString(R.string.save_slot))
                .setItems(options) { _, which ->
                    onSlotPicked(slots[which])
                }
                .setNegativeButton(R.string.cancel) { dialog, _ ->
                    dialog.cancel()
                }
                .setOnCancelListener { resumeEmulation() }
                .show()
    }
}