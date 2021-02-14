package me.magnum.melonds.utils

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.utils.FileUtils.getAbsolutePathFromSAFUri
import java.io.File
import java.io.FileNotFoundException

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
        PRESENT, MISSING, INVALID
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

        val requiredFiles = getRequiredFiles(consoleType)
        val fileResults = if (consoleType == ConsoleType.DS) {
            listOf(
                    "bios7.bin" to getDSBios7Status(context, dir),
                    "bios9.bin" to getDSBios9Status(context, dir),
                    "firmware.bin" to getDSFirmwareStatus(context, dir)
            )
        } else {
            listOf(
                    "bios7.bin" to getDSiBios7Status(context, dir),
                    "bios9.bin" to getDSiBios9Status(context, dir),
                    "firmware.bin" to getDSiFirmwareStatus(context, dir),
                    "nand.bin" to getDSiNandStatus(dir)
            )
        }
        val result = if (fileResults.any { it.second != ConfigurationFileStatus.PRESENT }) {
            ConfigurationDirStatus.INVALID
        } else {
            ConfigurationDirStatus.VALID
        }
        return ConfigurationDirResult(result, requiredFiles, fileResults.toTypedArray())
    }

    private fun getDSBios7Status(context: Context, configurationDir: File): ConfigurationFileStatus {
        return getBiosFileStatus(context, configurationDir, "bios7.bin", 0x4000.toLong())
    }

    private fun getDSBios9Status(context: Context, configurationDir: File): ConfigurationFileStatus {
        return getBiosFileStatus(context, configurationDir, "bios9.bin", 0x1000.toLong())
    }

    private fun getDSFirmwareStatus(context: Context, configurationDir: File): ConfigurationFileStatus {
        val firmwareDocument = DocumentFile.fromFile(File(configurationDir, "firmware.bin"))
        return try {
            context.contentResolver.openAssetFileDescriptor(firmwareDocument.uri, "r")?.use {
                when (it.length) {
                    AssetFileDescriptor.UNKNOWN_LENGTH -> ConfigurationFileStatus.MISSING
                    0x20000.toLong(),
                    0x40000.toLong(),
                    0x80000.toLong() -> ConfigurationFileStatus.PRESENT
                    else -> ConfigurationFileStatus.INVALID
                }
            } ?: ConfigurationFileStatus.MISSING
        } catch (e: FileNotFoundException) {
            ConfigurationFileStatus.MISSING
        }
    }

    private fun getDSiBios7Status(context: Context, configurationDir: File): ConfigurationFileStatus {
        return getBiosFileStatus(context, configurationDir, "bios7.bin", 0x10000.toLong())
    }

    private fun getDSiBios9Status(context: Context, configurationDir: File): ConfigurationFileStatus {
        return getBiosFileStatus(context, configurationDir, "bios9.bin", 0x10000.toLong())
    }

    private fun getDSiFirmwareStatus(context: Context, configurationDir: File): ConfigurationFileStatus {
        val firmwareDocument = DocumentFile.fromFile(File(configurationDir, "firmware.bin"))
        return try {
            context.contentResolver.openAssetFileDescriptor(firmwareDocument.uri, "r")?.use {
                when (it.length) {
                    AssetFileDescriptor.UNKNOWN_LENGTH -> ConfigurationFileStatus.MISSING
                    0x20000.toLong() -> ConfigurationFileStatus.PRESENT
                    else -> ConfigurationFileStatus.INVALID
                }
            } ?: ConfigurationFileStatus.MISSING
        } catch (e: FileNotFoundException) {
            ConfigurationFileStatus.MISSING
        }
    }

    private fun getDSiNandStatus(configurationDir: File): ConfigurationFileStatus {
        val firmwareDocument = DocumentFile.fromFile(File(configurationDir, "nand.bin"))
        return if (firmwareDocument.isFile) ConfigurationFileStatus.PRESENT else ConfigurationFileStatus.MISSING
    }

    private fun getBiosFileStatus(context: Context, configurationDir: File, fileName: String, requiredSize: Long): ConfigurationFileStatus {
        val biosDocument = DocumentFile.fromFile(File(configurationDir, fileName))
        return try {
            context.contentResolver.openAssetFileDescriptor(biosDocument.uri, "r")?.use {
                when (it.length) {
                    AssetFileDescriptor.UNKNOWN_LENGTH -> ConfigurationFileStatus.MISSING
                    requiredSize -> ConfigurationFileStatus.PRESENT
                    else -> ConfigurationFileStatus.INVALID
                }
            } ?: ConfigurationFileStatus.MISSING
        } catch (e: FileNotFoundException) {
            ConfigurationFileStatus.MISSING
        }
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