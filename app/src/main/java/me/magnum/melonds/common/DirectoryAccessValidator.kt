package me.magnum.melonds.common

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * Allows directory access to be validated. Some Android devices allow compressed files to be selected as directories, which do not grant write access. To circumvent this,
 * this class allows such directories to be identified through [getDirectoryAccessForPermission].
 */
class DirectoryAccessValidator(private val context: Context) {

    enum class DirectoryAccessResult {
        /**
         * The directory has all the required permissions.
         */
        OK,

        /**
         * The directory exists but it cannot be written to.
         */
        READ_ONLY,

        /**
         * The directory has not been found.
         */
        NOT_FOUND
    }

    companion object {
        private val readOnlyExtensions = listOf("zip", "7z", "rar", "tar")
    }

    fun getDirectoryAccessForPermission(directoryUri: Uri, permission: Permission): DirectoryAccessResult {
        val directoryDocument = DocumentFile.fromTreeUri(context, directoryUri) ?: return DirectoryAccessResult.NOT_FOUND
        val directoryName = directoryDocument.name ?: return DirectoryAccessResult.NOT_FOUND

        // If only read access is required, we don't need to check if we might be dealing with unwanted directories
        if (permission == Permission.READ) {
            return DirectoryAccessResult.OK
        }

        // Not sure if checking for this will detect compressed files. Fallback logic is below in case compressed files are considered as directories
        if (directoryDocument.isFile) {
            return DirectoryAccessResult.READ_ONLY
        }

        val extension = directoryName.substringAfterLast('.', "")
        if (extension.isEmpty()) {
            // Directory has no extension in the name. Assume it's not a file
            return DirectoryAccessResult.OK
        }

        return if (readOnlyExtensions.contains(extension)) {
            DirectoryAccessResult.READ_ONLY
        } else {
            DirectoryAccessResult.OK
        }
    }
}