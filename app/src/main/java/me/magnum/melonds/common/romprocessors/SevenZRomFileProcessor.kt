package me.magnum.melonds.common.romprocessors

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.core.content.getSystemService
import me.magnum.melonds.R
import me.magnum.melonds.common.uridelegates.UriHandler
import me.magnum.melonds.domain.model.SizeUnit
import me.magnum.melonds.impl.NdsRomCache
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.FileInputStream
import java.io.InputStream

class SevenZRomFileProcessor(private val context: Context, private val uriHandler: UriHandler, ndsRomCache: NdsRomCache) : CompressedRomFileProcessor(context, uriHandler, ndsRomCache) {

    companion object {
        private const val TAG = "SevenZRomFileProcessor"
        private const val OUT_OF_MEMORY_NOTIFICATION_COOLDOWN_MS = 10000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val outOfMemoryNotificationTimes = mutableMapOf<Uri, Long>()

    override fun getNdsEntryStreamInFileStream(fileStream: InputStream, romUri: Uri): RomFileStream? {
        if (fileStream !is FileInputStream) {
            return null
        }

        val deviceMemory = context.getSystemService<ActivityManager>()?.let {
            val memoryInfo = ActivityManager.MemoryInfo()
            it.getMemoryInfo(memoryInfo)
            SizeUnit.Bytes(memoryInfo.totalMem)
        } ?: SizeUnit.Bytes(Int.MAX_VALUE.toLong())

        try {
            val sevenZFile = SevenZFile.Builder()
                .setMaxMemoryLimitKb((deviceMemory * 0.1f).toKB().toInt())
                .setSeekableByteChannel(fileStream.channel)
                .get()
            return getNdsEntryInFile(sevenZFile)?.let {
                RomFileStream(sevenZFile.getInputStream(it), SizeUnit.Bytes(it.size))
            }
        }
        catch (e: OutOfMemoryError) {
            Log.e(TAG, "Failed to load 7z ROM contents", e)
            notifyOutOfMemory(romUri)
        }

        return null
    }

    /**
     * Informs the user that the given file could not be extracted due to a lack of memory. Since the same file may be processed multiple times in quick
     * succession (to fetch its metadata, icon, etc.), notifications for the same file are throttled to avoid spamming the user.
     */
    private fun notifyOutOfMemory(romUri: Uri) {
        val now = SystemClock.elapsedRealtime()
        synchronized(outOfMemoryNotificationTimes) {
            val lastNotificationTime = outOfMemoryNotificationTimes[romUri]
            if (lastNotificationTime != null && now - lastNotificationTime < OUT_OF_MEMORY_NOTIFICATION_COOLDOWN_MS) {
                return
            }
            outOfMemoryNotificationTimes[romUri] = now
        }

        val fileName = uriHandler.getUriDocument(romUri)?.name ?: romUri.lastPathSegment.orEmpty()
        mainHandler.post {
            Toast.makeText(context, context.getString(R.string.error_rom_out_of_memory, fileName), Toast.LENGTH_LONG).show()
        }
    }

    private fun getNdsEntryInFile(sevenZFile: SevenZFile): SevenZArchiveEntry? {
        do {
            val nextEntry = sevenZFile.nextEntry ?: break
            if (!nextEntry.isDirectory && isSupportedRomFile(nextEntry.name)) {
                return nextEntry
            }
        } while (true)
        return null
    }
}
