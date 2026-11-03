package me.magnum.melonds.utils

import android.app.Application
import android.net.Uri
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.domain.model.rom.config.RomConfig
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class RomProcessorSecurityTest {
    @Test
    fun `valid metadata accepts legal short reads from a provider`() {
        val rom = ByteArray(0xC00)
        "ABCD".toByteArray().copyInto(rom, 0x0C)
        ByteBuffer.wrap(rom).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0x20, 0x160)
            putInt(0x2C, 0)
            putInt(0x30, 0x160)
            putInt(0x3C, 0)
            putInt(0x68, 0x200)
        }
        "Game\nDeveloper".toByteArray(Charsets.UTF_16LE).copyInto(rom, 0x200 + 0x340)

        val shortReads = object : ByteArrayInputStream(rom) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                super.read(buffer, offset, minOf(length, 3))
        }

        assertNotNull(RomProcessor.getRomMetadata(shortReads))
    }

    @Test
    fun `icon and info accept legal short reads`() {
        val romBytes = minimumRom()
        val rom = testRom()

        assertNotNull(RomProcessor.getRomIcon(shortReads(romBytes)))
        assertNotNull(RomProcessor.getRomInfo(rom, shortReads(romBytes)))
    }

    @Test
    fun `icon rejects an oversized banner offset before scanning`() {
        val header = ByteArray(0x6C)
        ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).putInt(0x68, 64 * 1024 * 1024)

        assertThrows(IOException::class.java) { RomProcessor.getRomIcon(ByteArrayInputStream(header)) }
    }

    @Test
    fun `truncated icon and info are rejected`() {
        val truncatedIcon = minimumRom().copyOf(0x200 + 32 + 100)

        assertThrows(IOException::class.java) { RomProcessor.getRomIcon(ByteArrayInputStream(truncatedIcon)) }
        assertNull(RomProcessor.getRomInfo(testRom(), ByteArrayInputStream(ByteArray(0x1FF))))
    }

    private fun minimumRom(): ByteArray = ByteArray(0xC00).also { rom ->
        "ABCD".toByteArray().copyInto(rom, 0x0C)
        ByteBuffer.wrap(rom).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0x20, 0x160)
            putInt(0x2C, 0)
            putInt(0x30, 0x160)
            putInt(0x3C, 0)
            putInt(0x68, 0x200)
        }
        "Game\nDeveloper".toByteArray(Charsets.UTF_16LE).copyInto(rom, 0x200 + 0x340)
    }

    private fun shortReads(bytes: ByteArray) = object : ByteArrayInputStream(bytes) {
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, minOf(length, 3))
    }

    private fun testRom() = Rom(
        name = "Game",
        developerName = "Developer",
        fileName = "game.nds",
        uri = Uri.parse("content://test/game"),
        parentTreeUri = null,
        config = RomConfig.default(),
        isDsiWareTitle = false,
        retroAchievementsHash = "",
    )
}
