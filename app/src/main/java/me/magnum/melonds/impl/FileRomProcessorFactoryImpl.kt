package me.magnum.melonds.impl

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import me.magnum.melonds.impl.romprocessors.NdsRomProcessor
import me.magnum.melonds.impl.romprocessors.ZipRomProcessor

class FileRomProcessorFactoryImpl(private val context: Context) : FileRomProcessorFactory {
    private val prefixProcessorMap = mapOf(
            "nds" to NdsRomProcessor(context),
            "zip" to ZipRomProcessor(context)
    )

    override fun getFileRomProcessorForDocument(romDocument: DocumentFile): FileRomProcessor? {
        val fileName = romDocument.name ?: return null
        val lastDotIndex = fileName.lastIndexOf('.')
        if (lastDotIndex < 0) return null

        val extension = fileName.substring(lastDotIndex + 1)
        return prefixProcessorMap[extension]
    }

    override fun getFileRomProcessorForDocument(romUri: Uri): FileRomProcessor? {
        val romDocument = DocumentFile.fromSingleUri(context, romUri) ?: return null
        return getFileRomProcessorForDocument(romDocument)
    }
}