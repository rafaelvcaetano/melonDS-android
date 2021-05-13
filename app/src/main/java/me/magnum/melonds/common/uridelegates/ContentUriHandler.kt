package me.magnum.melonds.common.uridelegates

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

class ContentUriHandler(private val context: Context) : UriHandler {
    override fun fileExists(uri: Uri): Boolean {
        return getUriDocument(uri)?.exists() == true
    }

    override fun createFileDocument(uri: Uri): DocumentFile? {
        // We can't reliably create a document from a target URI. We need the parent tree URI and document name at least.
        return null
    }

    override fun getUriDocument(uri: Uri): DocumentFile? {
        return DocumentFile.fromSingleUri(context, uri)
    }

    override fun getUriTreeDocument(uri: Uri): DocumentFile? {
        return DocumentFile.fromTreeUri(context, uri)
    }
}