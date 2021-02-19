package me.magnum.melonds.impl

import android.net.Uri
import androidx.documentfile.provider.DocumentFile

interface FileRomProcessorFactory {
    fun getFileRomProcessorForDocument(romDocument: DocumentFile): FileRomProcessor?
    fun getFileRomProcessorForDocument(romUri: Uri): FileRomProcessor?
}