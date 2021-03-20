package me.magnum.melonds.common.uridelegates

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.net.URLDecoder

class ContentUriHandler(private val context: Context) : UriHandler {
    override fun fileExists(uri: Uri): Boolean {
        return DocumentFile.fromSingleUri(context, uri)?.exists() == true
    }

    override fun createFileDocument(uri: Uri): DocumentFile? {
        val stringUri = uri.toString()
        val decodedUri = URLDecoder.decode(stringUri, Charsets.UTF_8.name())
        val fileName = decodedUri.substring(decodedUri.lastIndexOf("/") + 1)

        // TODO: What if the parent dir is the root?
        val parentUriString = stringUri.substringBeforeLast("%2F")
        val parentUri = Uri.parse(parentUriString)
        val parentDocument = DocumentFile.fromTreeUri(context, parentUri)
        return parentDocument?.createFile("*/*", fileName)
    }

    override fun getUriDocument(uri: Uri): DocumentFile? {
        return DocumentFile.fromSingleUri(context, uri)
    }

    override fun getUriTreeDocument(uri: Uri): DocumentFile? {
        return DocumentFile.fromTreeUri(context, uri)
    }
}