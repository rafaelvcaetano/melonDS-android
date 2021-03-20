package me.magnum.melonds.common

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import me.magnum.melonds.common.uridelegates.ContentUriHandler
import me.magnum.melonds.common.uridelegates.StandardFileUriHandler

class UriFileHandler(private val context: Context) {
    companion object {
        private const val FILE_NOT_FOUND = -1
        private val readModeChars = listOf("r", "a+", "r+", "w+")
        private val writeModeChars = listOf("w", "a", "+")
    }

    private val modeStringBuilder = StringBuilder()
    private val uriHandlers = mapOf(
            "content" to ContentUriHandler(context),
            "file" to StandardFileUriHandler()
    )

    fun open(uriString: String, mode: String): Int {
        val uri = Uri.parse(uriString)

        var isWriteMode = false
        modeStringBuilder.clear()

        if (mode.findAnyOf(readModeChars) != null) {
            modeStringBuilder.append("r")
        }
        if (mode.findAnyOf(writeModeChars) != null) {
            modeStringBuilder.append("w")
            isWriteMode = true
        }

        val internalMode = modeStringBuilder.toString()

        return if (isWriteMode) {
            uriHandlers[uri.scheme]?.let {
                if (it.fileExists(uri)) {
                    context.contentResolver.openFileDescriptor(uri, internalMode)?.detachFd()
                } else {
                    it.createFileDocument(uri)?.let { document ->
                        context.contentResolver.openFileDescriptor(document.uri, internalMode)?.detachFd()
                    }
                }
            } ?: FILE_NOT_FOUND
        } else {
            try {
                context.contentResolver.openFileDescriptor(uri, internalMode)?.detachFd() ?: FILE_NOT_FOUND
            } catch (e: Exception) {
                FILE_NOT_FOUND
            }
        }
    }

    fun getUriDocument(uri: Uri): DocumentFile? {
        return uriHandlers[uri.scheme]?.getUriDocument(uri)
    }

    fun getUriTreeDocument(uri: Uri): DocumentFile? {
        return uriHandlers[uri.scheme]?.getUriTreeDocument(uri)
    }
}