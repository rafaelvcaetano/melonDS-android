package me.magnum.melonds.utils;

/**
 * Utility class to convert directory preference values (from
 * {@link com.github.angads25.filepicker.view.FilePickerPreference} into directory paths ready to
 * use.
 */
public final class PreferenceDirectoryUtils {
	private PreferenceDirectoryUtils() {
	}

	/**
	 * Retrieves a single directory from the given directory preference value. If multiple
	 * directories are stored in the preference, only the first value is returned. If no directory
	 * is stored in the preference value, {@code null} is returned. Directories are assumed to be
	 * splitted by a column (:).
	 *
	 * @param preferenceValue The directory preference value
	 * @return The first directory found in the preference or {@code null} if there is none
	 */
	public static String getSingleDirectoryFromPreference(String preferenceValue) {
		String[] parts = getMultipleDirectoryFromPreference(preferenceValue);
		if (parts.length > 0)
			return parts[0];

		return null;
	}

	/**
	 * Retrieves all directory from the given directory preference value. If no directory is stored
	 * in the preference value, an empty array is returned. Directories are assumed to be splitted
	 * by a column (:).
	 *
	 * @param preferenceValue The directory preference value
	 * @return The directories found in the preference
	 */
	public static String[] getMultipleDirectoryFromPreference(String preferenceValue) {
		if (preferenceValue == null)
			return new String[0];

		return preferenceValue.split(":");
	}
}
