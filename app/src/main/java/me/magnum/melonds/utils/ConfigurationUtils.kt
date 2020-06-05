package me.magnum.melonds.utils

import java.io.File

object ConfigurationUtils {
    enum class ConfigurationDirStatus {
        UNSET, INVALID, VALID
    }

    @JvmStatic
	fun checkConfigurationDirectory(configurationDir: String?): ConfigurationDirStatus {
        if (configurationDir == null)
            return ConfigurationDirStatus.UNSET

        val dir = File(configurationDir)
        if (!dir.isDirectory)
            return ConfigurationDirStatus.INVALID

        val bios7File = File(dir, "bios7.bin")
        val bios9File = File(dir, "bios9.bin")
        val firmwareFile = File(dir, "firmware.bin")

        return if (!bios7File.isFile || !bios9File.isFile || !firmwareFile.isFile)
            ConfigurationDirStatus.INVALID
        else
            ConfigurationDirStatus.VALID
    }
}