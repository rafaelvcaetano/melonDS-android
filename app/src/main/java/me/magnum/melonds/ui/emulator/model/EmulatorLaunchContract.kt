package me.magnum.melonds.ui.emulator.model

import android.content.Context
import android.content.Intent
import android.net.Uri
import me.magnum.melonds.ui.emulator.EmulatorActivity

/** Keys accepted by the exported emulator launcher. Keep this object free of native initializers. */
object EmulatorLaunchContract {
    const val KEY_ROM = "rom"
    const val KEY_PATH = "PATH"
    const val KEY_URI = "uri"
    const val KEY_URI_AUTHORIZED = "uri_authorized"
    const val KEY_BOOT_FIRMWARE_CONSOLE = "boot_firmware_console"
    const val KEY_BOOT_FIRMWARE_ONLY = "boot_firmware_only"

    fun createRomIntent(context: Context, uri: Uri) =
        Intent(context, EmulatorActivity::class.java).apply {
            action = "${context.packageName}.LAUNCH_ROM"
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
}
