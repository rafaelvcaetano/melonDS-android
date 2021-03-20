package me.magnum.melonds.common.uridelegates

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

class StandardFileUriHandler : UriHandler {
    override fun fileExists(uri: Uri): Boolean {
        return uri.path?.let {
            File(it).isFile
        } ?: false
    }

    override fun createFileDocument(uri: Uri): DocumentFile? {
        val filePath = uri.path?.let { File(it) } ?: return null
        val fileName = filePath.name
        val parentDocument = filePath.parentFile?.let { DocumentFile.fromFile(it) } ?: return null
        return parentDocument.createFile("*/*", fileName)
    }

    override fun getUriDocument(uri: Uri): DocumentFile? {
        return uri.path?.let {
            DocumentFile.fromFile(File(it))
        }
    }

    override fun getUriTreeDocument(uri: Uri): DocumentFile? {
        return getUriDocument(uri)
    }
}