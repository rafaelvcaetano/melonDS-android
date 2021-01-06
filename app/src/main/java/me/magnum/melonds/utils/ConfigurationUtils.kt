package me.magnum.melonds.utils

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.utils.FileUtils.getAbsolutePathFromSAFUri
import java.io.File

object ConfigurationUtils {
    data class ConfigurationDirResult(
            val status: ConfigurationDirStatus,
            val requiredFiles: Array<String>,
            val fileResults: Array<Pair<String, ConfigurationFileStatus>>
    )

    enum class ConfigurationDirStatus {
        UNSET, INVALID, VALID
    }

    enum class ConfigurationFileStatus {
        PRESENT, MISSING
    }

    @JvmStatic
	fun checkConfigurationDirectory(context: Context, configurationDir: Uri?, consoleType: ConsoleType): ConfigurationDirResult {
        if (configurationDir == null) {
            val requiredFiles = getRequiredFiles(consoleType)
            val fileResults = requiredFiles.map { it to ConfigurationFileStatus.MISSING }
            return ConfigurationDirResult(ConfigurationDirStatus.UNSET, requiredFiles, fileResults.toTypedArray())
        }

        val dirPath = getAbsolutePathFromSAFUri(context, configurationDir)
        val dir = File(dirPath)
        if (!dir.isDirectory) {
            val requiredFiles = getRequiredFiles(consoleType)
            val fileResults = requiredFiles.map { it to ConfigurationFileStatus.MISSING }
            return ConfigurationDirResult(ConfigurationDirStatus.INVALID, requiredFiles, fileResults.toTypedArray())
        }

        var result = ConfigurationDirStatus.VALID
        val requiredFiles = getRequiredFiles(consoleType)
        val fileResults = requiredFiles.map {
            it to if (DocumentFile.fromFile(File(dir, it)).isFile)
                ConfigurationFileStatus.PRESENT
            else {
                result = ConfigurationDirStatus.INVALID
                ConfigurationFileStatus.MISSING
            }
        }
        return ConfigurationDirResult(result, requiredFiles, fileResults.toTypedArray())
    }

    private fun getRequiredFiles(consoleType: ConsoleType): Array<String> {
        return when(consoleType) {
            ConsoleType.DS -> arrayOf(
                    "bios7.bin",
                    "bios9.bin",
                    "firmware.bin"
            )
            ConsoleType.DSi -> arrayOf(
                    "bios7.bin",
                    "bios9.bin",
                    "firmware.bin",
                    "nand.bin"
            )
        }
    }
}