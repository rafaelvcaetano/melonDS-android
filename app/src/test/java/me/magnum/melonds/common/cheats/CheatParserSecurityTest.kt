package me.magnum.melonds.common.cheats

import me.magnum.melonds.domain.model.CheatDatabase
import me.magnum.melonds.domain.model.Game
import me.magnum.melonds.impl.stripParenthesizedSegments
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CheatParserSecurityTest {
    @Test
    fun `database version suffix stripping preserves ordinary names without regex backtracking`() {
        assertEquals("Database  Europe", stripParenthesizedSegments("Database (v1.2) Europe"))
        assertEquals("Database (unfinished", stripParenthesizedSegments("Database (unfinished"))
        assertEquals(")", stripParenthesizedSegments("((nested))"))
    }

    @Test
    fun `valid cheat XML remains importable with provider independent hardening`() {
        val xml = """<codelist><name>DB</name><game><name>Game</name><gameid>ABCD 1234</gameid><folder><name>Folder</name><cheat><name>Cheat</name><codes>00000000 00000000</codes></cheat></folder></game></codelist>"""
        var games = 0
        val listener = object : CheatDatabaseParserListener {
            override fun onDatabaseParseStart(databaseName: String) = CheatDatabase(1, databaseName)
            override fun onGameParseStart(gameName: String) = Unit
            override fun onGameParsed(game: Game) { games++ }
            override fun onParseComplete() = Unit
        }

        XmlCheatDatabaseParser().parseCheatDatabase(
            ProgressTrackerInputStream(ByteArrayInputStream(xml.toByteArray())), listener
        )
        assertEquals(1, games)
    }

    @Test
    fun `xml parser rejects doctype and internal entities`() {
        val xml = """<!DOCTYPE codelist [<!ENTITY boom "expanded">]>
            <codelist><name>&boom;</name></codelist>""".trimIndent()

        try {
            XmlCheatDatabaseParser().parseCheatDatabase(
                ProgressTrackerInputStream(ByteArrayInputStream(xml.toByteArray())),
                listener(),
            )
            fail("DOCTYPE was accepted")
        } catch (_: Exception) {
            // Expected: importing untrusted cheat XML must fail closed.
        }
    }

    @Test
    fun `doctype markers in comments and cdata remain ordinary text`() {
        val xml = """<codelist><!-- <!DOCTYPE harmless> --><name>DB</name><game><name>G</name><gameid>ABCD 1234</gameid><folder><name>F</name><cheat><name>C</name><note><![CDATA[<!ENTITY harmless>]]></note><codes>0 0</codes></cheat></folder></game></codelist>"""
        XmlCheatDatabaseParser().parseCheatDatabase(
            ProgressTrackerInputStream(ByteArrayInputStream(xml.toByteArray())), listener()
        )
    }

    @Test
    fun `xml parser bounds text collected for one field`() {
        val xml = "<codelist><name>${"x".repeat(1024 * 1024 + 1)}</name></codelist>"

        assertThrows(Exception::class.java) {
            XmlCheatDatabaseParser().parseCheatDatabase(
                ProgressTrackerInputStream(ByteArrayInputStream(xml.toByteArray())),
                listener(),
            )
        }
    }

    @Test
    fun `xml parser bounds aggregate cheats in one game`() {
        val cheats = buildString {
            repeat(10_001) { append("<cheat><name>C</name><codes>0 0</codes></cheat>") }
        }
        val xml = "<codelist><name>DB</name><game><name>G</name><gameid>ABCD 1234</gameid><folder><name>F</name>$cheats</folder></game></codelist>"
        assertThrows(Exception::class.java) {
            XmlCheatDatabaseParser().parseCheatDatabase(
                ProgressTrackerInputStream(ByteArrayInputStream(xml.toByteArray())), listener()
            )
        }
    }

    @Test
    fun `xml parser bounds aggregate text retained by one game`() {
        val payload = "x".repeat(1024 * 1024)
        val cheats = buildString {
            repeat(9) {
                append("<cheat><name>C</name><note>").append(payload)
                    .append("</note><codes>0 0</codes></cheat>")
            }
        }
        val xml = "<codelist><name>DB</name><game><name>G</name><gameid>ABCD 1234</gameid><folder><name>F</name>$cheats</folder></game></codelist>"
        assertThrows(Exception::class.java) {
            XmlCheatDatabaseParser().parseCheatDatabase(
                ProgressTrackerInputStream(ByteArrayInputStream(xml.toByteArray())), listener()
            )
        }
    }

    @Test
    fun `xml parser counts actual provider bytes even when declared length is unknown`() {
        val xml = "<codelist><name>${"x".repeat(256)}</name></codelist>"

        assertThrows(Exception::class.java) {
            XmlCheatDatabaseParser(maxInputBytes = 128).parseCheatDatabase(
                ProgressTrackerInputStream(ByteArrayInputStream(xml.toByteArray())),
                listener(),
            )
        }
    }

    @Test
    fun `usrcheat parser applies the same actual-byte document budget`() {
        val headerAndAddressTerminator = ByteArray(0x110)
        "R4 CheatCode".toByteArray().copyInto(headerAndAddressTerminator)

        assertThrows(Exception::class.java) {
            UsrCheatDatabaseParser(maxInputBytes = 0x100).parseCheatDatabase(
                ProgressTrackerInputStream(ByteArrayInputStream(headerAndAddressTerminator)),
                listener(),
            )
        }
    }

    @Test
    fun `usrcheat offsets are bounded before a large allocation`() {
        val prefix = ByteArray(0x200)
        "R4 CheatCode".toByteArray().copyInto(prefix)
        writeAddressEntry(prefix, 0x100, "GAME", 0x200)
        writeAddressEntry(prefix, 0x110, "NEXT", 0x20_0200)
        // Third entry is the zero terminator.
        val stream = MaxReadRecordingInputStream(prefix)

        runCatching {
            UsrCheatDatabaseParser().parseCheatDatabase(ProgressTrackerInputStream(stream), listener())
        }

        assertTrue(
            "parser requested a ${stream.maxRequestedBytes}-byte read before validating the entry span",
            stream.maxRequestedBytes <= 8 * 1024,
        )
    }

    @Test
    fun `usrcheat folder count cannot exceed the remaining game item budget`() {
        assertTrue(usrCheatFolderSpanFits(itemsParsed = 0, numItems = 10_000, folderItems = 9_999))
        assertTrue(!usrCheatFolderSpanFits(itemsParsed = 0, numItems = 10_000, folderItems = 10_000))
        assertTrue(!usrCheatFolderSpanFits(itemsParsed = 9_999, numItems = 10_000, folderItems = 1))
    }

    private fun writeAddressEntry(target: ByteArray, offset: Int, gameCode: String, dataOffset: Int) {
        val buffer = ByteBuffer.wrap(target, offset, 16).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(gameCode.toByteArray())
        buffer.putInt(0)
        buffer.putInt(dataOffset)
        buffer.putInt(0)
    }

    private fun listener() = object : CheatDatabaseParserListener {
        override fun onDatabaseParseStart(databaseName: String) = CheatDatabase(1, databaseName)
        override fun onGameParseStart(gameName: String) = Unit
        override fun onGameParsed(game: Game) = Unit
        override fun onParseComplete() = Unit
    }

    private class MaxReadRecordingInputStream(private val bytes: ByteArray) : InputStream() {
        private var position = 0
        var maxRequestedBytes = 0
            private set

        override fun read(): Int = if (position < bytes.size) bytes[position++].toInt() and 0xff else -1

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            maxRequestedBytes = maxOf(maxRequestedBytes, length)
            if (position >= bytes.size) return -1
            val count = minOf(length, bytes.size - position)
            bytes.copyInto(buffer, offset, position, position + count)
            position += count
            return count
        }
    }
}
