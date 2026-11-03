package me.magnum.melonds.impl

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import me.magnum.melonds.domain.model.SizeUnit
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.domain.model.rom.config.RomConfig
import me.magnum.melonds.domain.repositories.SettingsRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.OutputStream
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class NdsRomCacheSecurityTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.externalCacheDir?.resolve("extracted_roms")?.deleteRecursively()
    }

    @Test
    fun `colliding uri hash codes do not alias cache entries`() {
        val cache = NdsRomCache(context, settings(SizeUnit.MB(16)))
        val first = rom("content://roms/Aa")
        val colliding = rom("content://roms/BB")
        check(first.uri.hashCode() == colliding.uri.hashCode())

        cache.cacheRom(first, extractor(SizeUnit.Bytes(4), byteArrayOf(1, 2, 3, 4)))

        assertNull(cache.getCachedRomFile(colliding))
    }

    @Test
    fun `declared entry larger than cache limit is rejected before writing`() {
        val cache = NdsRomCache(context, settings(SizeUnit.Bytes(8)))
        var writerCalled = false

        runCatching {
            cache.cacheRom(rom("content://roms/large"), object : NdsRomCache.RomExtractor {
                override fun getExtractedRomFileSize() = SizeUnit.Bytes(9)
                override fun saveRomFile(fileStream: OutputStream): Boolean {
                    writerCalled = true
                    fileStream.write(ByteArray(9))
                    return true
                }
            })
        }

        assertFalse("oversized extractor was invoked", writerCalled)
    }

    @Test
    fun `extractor cannot write more bytes than it declared`() {
        val cache = NdsRomCache(context, settings(SizeUnit.Bytes(32)))

        runCatching {
            cache.cacheRom(rom("content://roms/lying"), extractor(SizeUnit.Bytes(4), ByteArray(12)))
        }

        assertNull(cache.getCachedRomFile(rom("content://roms/lying")))
    }

    @Test
    fun `unknown archive entry size uses configured cap instead of a negative limit`() {
        val cache = NdsRomCache(context, settings(SizeUnit.Bytes(16)))
        val rom = rom("content://roms/data-descriptor")
        cache.cacheRom(rom, object : NdsRomCache.RomExtractor {
            override fun getExtractedRomFileSize(): SizeUnit? = null
            override fun saveRomFile(fileStream: OutputStream): Boolean {
                fileStream.write(ByteArray(12))
                return true
            }
        })

        assertFalse(cache.getCachedRomFile(rom) == null)
    }

    @Test
    fun `small unknown entry does not evict unrelated cached roms`() {
        val cache = NdsRomCache(context, settings(SizeUnit.Bytes(32)))
        val existing = rom("content://roms/existing")
        val unknown = rom("content://roms/unknown-small")
        cache.cacheRom(existing, extractor(SizeUnit.Bytes(12), ByteArray(12)))

        cache.cacheRom(unknown, object : NdsRomCache.RomExtractor {
            override fun getExtractedRomFileSize(): SizeUnit? = null
            override fun saveRomFile(fileStream: OutputStream): Boolean {
                fileStream.write(ByteArray(8))
                return true
            }
        })

        assertNotNull(cache.getCachedRomFile(existing))
        assertNotNull(cache.getCachedRomFile(unknown))
    }

    @Test
    fun `unknown entry extraction never exceeds the configured cache budget`() {
        val maxBytes = 32L
        val cache = NdsRomCache(context, settings(SizeUnit.Bytes(maxBytes)))
        cache.cacheRom(
            rom("content://roms/existing-full-cache"),
            extractor(SizeUnit.Bytes(24), ByteArray(24)),
        )

        var peakBytes = cacheBytesOnDisk()
        cache.cacheRom(rom("content://roms/unknown-budgeted"), object : NdsRomCache.RomExtractor {
            override fun getExtractedRomFileSize(): SizeUnit? = null

            override fun saveRomFile(fileStream: OutputStream): Boolean {
                repeat(2) {
                    fileStream.write(ByteArray(8))
                    peakBytes = maxOf(peakBytes, cacheBytesOnDisk())
                }
                return true
            }
        })

        assertTrue("cache peaked at $peakBytes bytes with a $maxBytes-byte limit", peakBytes <= maxBytes)
    }

    private fun cacheBytesOnDisk(): Long = context.externalCacheDir
        ?.resolve("extracted_roms")
        ?.listFiles()
        ?.sumOf { it.length() }
        ?: 0L

    private fun extractor(size: SizeUnit, bytes: ByteArray) = object : NdsRomCache.RomExtractor {
        override fun getExtractedRomFileSize() = size
        override fun saveRomFile(fileStream: OutputStream): Boolean {
            fileStream.write(bytes)
            return true
        }
    }

    private fun settings(maxSize: SizeUnit): SettingsRepository {
        return Proxy.newProxyInstance(
            SettingsRepository::class.java.classLoader,
            arrayOf(SettingsRepository::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getRomCacheMaxSize" -> maxSize
                else -> error("Unexpected settings access: ${method.name}")
            }
        } as SettingsRepository
    }

    private fun rom(uri: String) = Rom(
        name = uri,
        developerName = "",
        fileName = "game.nds",
        uri = Uri.parse(uri),
        parentTreeUri = null,
        config = RomConfig.default(),
        isDsiWareTitle = false,
        retroAchievementsHash = "",
    )
}
