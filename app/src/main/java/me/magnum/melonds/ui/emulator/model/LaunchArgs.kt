package me.magnum.melonds.ui.emulator.model

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.rom.Rom

sealed class LaunchArgs {
    data class RomObject(val rom: Rom) : LaunchArgs()
    data class RomUri(val uri: Uri) : LaunchArgs()
    data class RomPath(val path: String) : LaunchArgs()
    data class Firmware(val consoleType: ConsoleType) : LaunchArgs()

    fun matchesRunningState(state: EmulatorState): Boolean {
        return when (this) {
            is RomObject -> state is EmulatorState.RunningRom && rom.uri == state.rom.uri
            is RomUri -> state is EmulatorState.RunningRom && uri == state.rom.uri
            is RomPath -> state is EmulatorState.RunningRom && state.rom.uri.path == path
            is Firmware -> state is EmulatorState.RunningFirmware && consoleType == state.console
        }
    }

    companion object {
        fun fromSavedStateHandle(savedStateHandle: SavedStateHandle): LaunchArgs? {
            val bootFirmware = savedStateHandle.get<Any>(EmulatorLaunchContract.KEY_BOOT_FIRMWARE_ONLY) as? Boolean
            return if (bootFirmware == true) {
                val consoleTypeParameter = savedStateHandle.get<Any>(EmulatorLaunchContract.KEY_BOOT_FIRMWARE_CONSOLE) as? Int
                if (consoleTypeParameter != null) {
                    ConsoleType.entries.getOrNull(consoleTypeParameter)?.let(::Firmware)
                } else {
                    null
                }
            } else {
                val uri = when (val uriEntry = savedStateHandle.get<Any>(EmulatorLaunchContract.KEY_URI)) {
                    is String -> uriEntry.toUri()
                    is Uri -> uriEntry
                    else -> null
                }

                val authorized = savedStateHandle.get<Any>(EmulatorLaunchContract.KEY_URI_AUTHORIZED) as? Boolean
                if (uri?.scheme == "content" && authorized == true) {
                    RomUri(uri)
                } else null
            }
        }

        fun fromIntent(intent: Intent): LaunchArgs? {
            val extras = runCatching { intent.extras }.getOrNull()
            @Suppress("DEPRECATION")
            val bootFirmwareOnly = runCatching {
                extras?.get(EmulatorLaunchContract.KEY_BOOT_FIRMWARE_ONLY) as? Boolean
            }.getOrNull() == true

            return if (bootFirmwareOnly) {
                @Suppress("DEPRECATION")
                val consoleTypeParameter = runCatching {
                    extras?.get(EmulatorLaunchContract.KEY_BOOT_FIRMWARE_CONSOLE) as? Int
                }.getOrNull()
                consoleTypeParameter?.let { ConsoleType.entries.getOrNull(it) }?.let(::Firmware)
            } else {
                authorizedContentUri(intent)?.let(::RomUri)
            }
        }

        fun authorizedContentUri(intent: Intent): Uri? {
            if (intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) return null
            // Android URI grants apply to Intent.data and ClipData, not to a
            // custom extra. Treat only the platform-authorized field as input.
            return intent.data?.takeIf { it.scheme == "content" }
        }
    }
}
