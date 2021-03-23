package me.magnum.melonds.common.uridelegates

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

class CompositeUriHandler(context: Context) : UriHandler {
    private val delegates = mapOf(
            "content" to ContentUriHandler(context),
            "file" to StandardFileUriHandler()
    )

    override fun fileExists(uri: Uri): Boolean {
        return delegates[uri.scheme]?.fileExists(uri) == true
    }

    override fun createFileDocument(uri: Uri): DocumentFile? {
        return delegates[uri.scheme]?.createFileDocument(uri)
    }

    override fun getUriDocument(uri: Uri): DocumentFile? {
        return delegates[uri.scheme]?.getUriDocument(uri)
    }

    override fun getUriTreeDocument(uri: Uri): DocumentFile? {
        return delegates[uri.scheme]?.getUriTreeDocument(uri)
    }

    override fun getParentUri(uri: Uri): Uri? {
        return delegates[uri.scheme]?.getParentUri(uri)
    }
}