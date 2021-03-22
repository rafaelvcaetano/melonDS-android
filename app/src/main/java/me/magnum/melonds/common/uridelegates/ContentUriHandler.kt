package me.magnum.melonds.common.uridelegates

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.net.URLDecoder

class ContentUriHandler(private val context: Context) : UriHandler {
    override fun fileExists(uri: Uri): Boolean {
        return getUriDocument(uri)?.exists() == true
    }

    override fun createFileDocument(uri: Uri): DocumentFile? {
        val fileName = getFileName(uri) ?: return null
        val parentUri = getParentUri(uri) ?: return null
        val parentDocument = DocumentFile.fromTreeUri(context, parentUri)
        return parentDocument?.createFile("*/*", fileName)
    }

    override fun getUriDocument(uri: Uri): DocumentFile? {
        return DocumentFile.fromSingleUri(context, uri)
    }

    override fun getUriTreeDocument(uri: Uri): DocumentFile? {
        return DocumentFile.fromTreeUri(context, uri)
    }

    override fun getParentUri(uri: Uri): Uri? {
        val stringUri = uri.toString()
        val documentSegmentIndex = stringUri.lastIndexOf("/document/")
        if (documentSegmentIndex < 0) {
            return null
        }

        var lastPathSeparatorIndex = stringUri.lastIndexOf("%2F")

        val parentUriString = if (lastPathSeparatorIndex < documentSegmentIndex) {
            lastPathSeparatorIndex = stringUri.lastIndexOf("%3A")
            if (lastPathSeparatorIndex < documentSegmentIndex) {
                return null
            }
            stringUri.substring(0, lastPathSeparatorIndex + 3)
        } else {
            stringUri.substring(0, lastPathSeparatorIndex)
        }

        return Uri.parse(parentUriString)
    }

    private fun getFileName(uri: Uri): String? {
        val stringUri = uri.toString()
        val decodedUri = URLDecoder.decode(stringUri, Charsets.UTF_8.name())
        val rootSeparatorIndex = decodedUri.lastIndexOf(":")
        if (rootSeparatorIndex < 0) {
            return null
        }

        val pathSeparatorIndex = decodedUri.lastIndexOf("/")
        return if (pathSeparatorIndex < rootSeparatorIndex) {
            decodedUri.substring(rootSeparatorIndex + 1)
        } else {
            decodedUri.substring(pathSeparatorIndex + 1)
        }
    }
}