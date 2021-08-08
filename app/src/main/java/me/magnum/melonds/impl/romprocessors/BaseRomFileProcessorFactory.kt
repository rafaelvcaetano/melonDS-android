package me.magnum.melonds.impl.romprocessors

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import me.magnum.melonds.common.romprocessors.RomFileProcessor
import me.magnum.melonds.common.romprocessors.RomFileProcessorFactory

abstract class BaseRomFileProcessorFactory(private val context: Context) : RomFileProcessorFactory {
    /**
     * Returns the [RomFileProcessor] to be used on files with the given [extension]. May return null if no processor exists that supports the given [extension].
     *
     * @param extension The extension (in lowercase) of the string for which the [RomFileProcessor] must be returned.
     * @return The [RomFileProcessor] to be used for the given [extension]. May be null if there's no suitable [RomFileProcessor].
     */
    abstract fun getRomFileProcessorForFileExtension(extension: String): RomFileProcessor?

    override fun getFileRomProcessorForDocument(romDocument: DocumentFile): RomFileProcessor? {
        val fileName = romDocument.name ?: return null
        val lastDotIndex = fileName.lastIndexOf('.')
        if (lastDotIndex < 0) return null

        val extension = fileName.substring(lastDotIndex + 1).lowercase()
        return getRomFileProcessorForFileExtension(extension)
    }

    override fun getFileRomProcessorForDocument(romUri: Uri): RomFileProcessor? {
        val romDocument = DocumentFile.fromSingleUri(context, romUri) ?: return null
        return getFileRomProcessorForDocument(romDocument)
    }
}