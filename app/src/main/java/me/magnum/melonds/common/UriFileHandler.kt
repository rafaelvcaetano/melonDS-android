package me.magnum.melonds.common

import android.content.Context
import android.net.Uri
import me.magnum.melonds.common.uridelegates.UriHandler

class UriFileHandler(private val context: Context, private val uriHandler: UriHandler) {
    companion object {
        private const val FILE_NOT_FOUND = -1
        private val readModeChars = listOf("r", "a+", "r+", "w+")
        private val writeModeChars = listOf("w", "a", "+")
    }

    fun open(uriString: String, mode: String): Int {
        val uri = Uri.parse(uriString)

        var isWriteMode = false

        val modeStringBuilder = StringBuilder()
        if (mode.findAnyOf(readModeChars) != null) {
            modeStringBuilder.append("r")
        }
        if (mode.findAnyOf(writeModeChars) != null) {
            modeStringBuilder.append("w")
            isWriteMode = true
        }

        val internalMode = modeStringBuilder.toString()

        return if (isWriteMode) {
            if (uriHandler.fileExists(uri)) {
                context.contentResolver.openFileDescriptor(uri, internalMode)?.detachFd()
            } else {
                uriHandler.createFileDocument(uri)?.let { document ->
                    context.contentResolver.openFileDescriptor(document.uri, internalMode)?.detachFd()
                }
            }
        } else {
            try {
                context.contentResolver.openFileDescriptor(uri, internalMode)?.detachFd()
            } catch (e: Exception) {
                null
            }
        } ?: FILE_NOT_FOUND
    }
}