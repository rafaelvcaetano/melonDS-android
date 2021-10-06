package me.magnum.melonds.impl

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.squareup.picasso.Picasso
import me.magnum.melonds.domain.model.Rom
import me.magnum.melonds.domain.model.SaveStateSlot
import java.io.File

class SaveStateScreenshotProvider(
    private val context: Context,
    private val picasso: Picasso
) {

    companion object {
        private const val SAVE_STATE_SCREENSHOTS_DIR = "ss_screenshots"
    }

    fun saveRomSaveStateScreenshot(rom: Rom, saveState: SaveStateSlot, screenshot: Bitmap) {
        val screenshotFile = getRomSaveStateScreenshotFile(rom, saveState, true) ?: return
        screenshotFile.outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }

        invalidateScreenshotFile(screenshotFile)
    }

    fun getRomSaveStateScreenshotUri(rom: Rom, saveState: SaveStateSlot): Uri? {
        val screenshotFile = getRomSaveStateScreenshotFile(rom, saveState) ?: return null
        return if (screenshotFile.isFile) {
            DocumentFile.fromFile(screenshotFile).uri
        } else {
            null
        }
    }

    fun deleteRomSaveStateScreenshot(rom: Rom, saveState: SaveStateSlot) {
        getRomSaveStateScreenshotFile(rom, saveState)?.let {
            invalidateScreenshotFile(it)
            it.delete()
        }
    }

    private fun getRomSaveStateScreenshotFile(rom: Rom, saveState: SaveStateSlot, createDirectoriesIfNeeded: Boolean = false): File? {
        val romDirectoryName = rom.uri.hashCode().toString()
        val romDirectory = File(getScreenshotsDir(), romDirectoryName)

        if (!romDirectory.isDirectory && createDirectoriesIfNeeded && !romDirectory.mkdirs()) {
            return null
        }

        return File(romDirectory, "${saveState.slot}.png")
    }

    private fun getScreenshotsDir(): File {
        return File(context.filesDir, SAVE_STATE_SCREENSHOTS_DIR)
    }

    private fun invalidateScreenshotFile(screenshotFile: File) {
        val screenshotDocument = DocumentFile.fromFile(screenshotFile)
        picasso.invalidate(screenshotDocument.uri)
    }
}