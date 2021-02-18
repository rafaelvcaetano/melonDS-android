package me.magnum.melonds

import android.content.res.AssetManager
import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.domain.model.EmulatorConfiguration
import me.magnum.melonds.domain.model.Input
import java.nio.ByteBuffer

object MelonEmulator {
    enum class LoadResult {
        SUCCESS, SUCCESS_GBA_FAILED, NDS_FAILED
    }

    enum class FirmwareLoadResult {
        SUCCESS,
        BIOS9_MISSING,
        BIOS9_BAD,
        BIOS7_MISSING,
        BIOS7_BAD,
        FIRMWARE_MISSING,
        FIRMWARE_BAD,
        FIRMWARE_NOT_BOOTABLE,
        DSI_BIOS9_MISSING,
        DSI_BIOS9_BAD,
        DSI_BIOS7_MISSING,
        DSI_BIOS7_BAD,
        DSI_NAND_MISSING,
        DSI_NAND_BAD
    }

	external fun setupEmulator(emulatorConfiguration: EmulatorConfiguration, assetManager: AssetManager?)

    external fun setupCheats(cheats: Array<Cheat>)

	fun loadRom(romPath: String, sramPath: String, loadDirect: Boolean, loadGbaRom: Boolean, gbaRomPath: String?, gbaSramPath: String?): LoadResult {
        val loadResult = loadRomInternal(romPath, sramPath, loadDirect, loadGbaRom, gbaRomPath, gbaSramPath)
        return when (loadResult) {
            0 -> LoadResult.SUCCESS
            1 -> LoadResult.SUCCESS_GBA_FAILED
            2 -> LoadResult.NDS_FAILED
            else -> throw RuntimeException("Unknown load result")
        }
    }

    fun bootFirmware(): FirmwareLoadResult {
        val loadResult = bootFirmwareInternal()
        return FirmwareLoadResult.values()[loadResult]
    }

    private external fun loadRomInternal(romPath: String, sramPath: String, loadDirect: Boolean, loadGbaRom: Boolean, gbaRomPath: String?, gbaSramPath: String?): Int

    private external fun bootFirmwareInternal(): Int

	external fun startEmulation()

	external fun copyFrameBuffer(frameBufferDst: ByteBuffer)

	external fun getFPS(): Int

	external fun pauseEmulation()

	external fun resumeEmulation()

    external fun resetEmulation(): Boolean

	external fun stopEmulation()

    external fun saveState(path: String): Boolean

    external fun loadState(path: String): Boolean

	external fun onScreenTouch(x: Int, y: Int)

	external fun onScreenRelease()

	fun onInputDown(input: Input) {
        onKeyPress(input.keyCode)
    }

	fun onInputUp(input: Input) {
        onKeyRelease(input.keyCode)
    }

    private external fun onKeyPress(key: Int)

    private external fun onKeyRelease(key: Int)

    external fun setFastForwardEnabled(enabled: Boolean)

    external fun updateEmulatorConfiguration(emulatorConfiguration: EmulatorConfiguration)
}