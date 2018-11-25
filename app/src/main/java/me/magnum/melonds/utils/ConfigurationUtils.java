package me.magnum.melonds.utils;

import java.io.File;

public final class ConfigurationUtils {
	public enum ConfigurationDirStatus {
		UNSET,
		INVALID,
		VALID
	}

	private ConfigurationUtils() {
	}

	public static ConfigurationDirStatus checkConfigurationDirectory(String configurationDir) {
		if (configurationDir == null)
			return ConfigurationDirStatus.UNSET;

		File dir = new File(configurationDir);
		if (!dir.isDirectory())
			return ConfigurationDirStatus.INVALID;

		File bios7File = new File(dir, "bios7.bin");
		File bios9File = new File(dir, "bios9.bin");
		File firmwareFile = new File(dir, "firmware.bin");

		if (!bios7File.isFile() || !bios9File.isFile() || !firmwareFile.isFile())
			return ConfigurationDirStatus.INVALID;

		return ConfigurationDirStatus.VALID;
	}
}
