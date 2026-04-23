package me.magnum.melonds.common

import android.content.Context
import androidx.core.net.toUri
import me.magnum.melonds.common.uridelegates.UriHandler
import java.io.FileNotFoundException

class UriFileHandler(private val context: Context, private val uriHandler: UriHandler) {
    companion object {
        private const val FILE_NOT_FOUND = -1
        private val writeModeChars = listOf("w", "a")
    }

    fun open(uriString: String, mode: String): Int {
        val uri = uriString.toUri()

        var isWriteMode = false
        if (mode.findAnyOf(writeModeChars) != null) {
            isWriteMode = true
        }

        return if (isWriteMode) {
            if (uriHandler.fileExists(uri)) {
                try {
                    context.contentResolver.openFileDescriptor(uri, mode)?.detachFd()
                } catch (_: FileNotFoundException) {
                    null
                }
            } else {
                uriHandler.createFileDocument(uri)?.let { document ->
                    try {
                        context.contentResolver.openFileDescriptor(document.uri, mode)?.detachFd()
                    } catch (_: FileNotFoundException) {
                        null
                    }
                }
            }
        } else {
            try {
                context.contentResolver.openFileDescriptor(uri, mode)?.detachFd()
            } catch (_: Exception) {
                null
            }
        } ?: FILE_NOT_FOUND
    }
}