package me.magnum.melonds.common.uridelegates

import android.net.Uri
import androidx.documentfile.provider.DocumentFile

interface UriHandler {
    fun fileExists(uri: Uri): Boolean
    fun createFileDocument(uri: Uri): DocumentFile?
    fun getUriDocument(uri: Uri): DocumentFile?
    fun getUriTreeDocument(uri: Uri): DocumentFile?
    fun getParentUri(uri: Uri): Uri?
}