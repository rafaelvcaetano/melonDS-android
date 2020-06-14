package me.magnum.melonds.utils

/**
 * Utility class to convert directory preference values (from
 * [com.github.angads25.filepicker.view.FilePickerPreference] into directory paths ready to
 * use.
 */
object PreferenceDirectoryUtils {
    /**
     * Retrieves a single directory from the given directory preference value. If multiple
     * directories are stored in the preference, only the first value is returned. If no directory
     * is stored in the preference value, `null` is returned. Directories are assumed to be
     * split by a column (:).
     *
     * @param preferenceValue The directory preference value
     * @return The first directory found in the preference or `null` if there is none
     */
    fun getSingleDirectoryFromPreference(preferenceValue: String?): String? {
        val parts = getMultipleDirectoryFromPreference(preferenceValue)
        return if (parts.isNotEmpty()) parts[0] else null
    }

    /**
     * Retrieves all directory from the given directory preference value. If no directory is stored
     * in the preference value, an empty array is returned. Directories are assumed to be split by a
     * column (:).
     *
     * @param preferenceValue The directory preference value
     * @return The directories found in the preference
     */
	@JvmStatic
	fun getMultipleDirectoryFromPreference(preferenceValue: String?): Array<String> {
        return preferenceValue?.split(":")
                ?.filter { it.isNotEmpty() }
                ?.toTypedArray() ?: emptyArray()
    }
}