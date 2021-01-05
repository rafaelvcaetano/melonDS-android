package me.magnum.melonds.utils

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.utils.FileUtils.getAbsolutePathFromSAFUri
import java.io.File

object ConfigurationUtils {
    enum class ConfigurationDirStatus {
        UNSET, INVALID, VALID
    }

    @JvmStatic
	fun checkConfigurationDirectory(context: Context, configurationDir: Uri?, consoleType: ConsoleType): ConfigurationDirStatus {
        if (configurationDir == null)
            return ConfigurationDirStatus.UNSET

        val dirPath = getAbsolutePathFromSAFUri(context, configurationDir)
        val dir = File(dirPath)
        if (!dir.isDirectory)
            return ConfigurationDirStatus.INVALID

        val requiredFiles = when(consoleType) {
            ConsoleType.DS -> listOf(
                    DocumentFile.fromFile(File(dir, "bios7.bin")),
                    DocumentFile.fromFile(File(dir, "bios9.bin")),
                    DocumentFile.fromFile(File(dir, "firmware.bin"))
            )
            ConsoleType.DSi -> listOf(
                    DocumentFile.fromFile(File(dir, "bios7.bin")),
                    DocumentFile.fromFile(File(dir, "bios9.bin")),
                    DocumentFile.fromFile(File(dir, "firmware.bin")),
                    DocumentFile.fromFile(File(dir, "nand.bin"))
            )
        }

        return if (requiredFiles.all { it.isFile })
            ConfigurationDirStatus.VALID
        else
            ConfigurationDirStatus.INVALID
    }
}