package me.magnum.melonds.common.cheats

import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.domain.model.CheatDatabase
import me.magnum.melonds.domain.model.CheatFolder
import me.magnum.melonds.domain.model.Game
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import kotlin.math.min

class UsrCheatDatabaseParser : CheatDatabaseParser {

    companion object {
        private const val MAGIC_STRING = "R4 CheatCode"
        private const val HEADER_SIZE = 0x100
        private const val DATABASE_NAME_OFFSET = 0x10
        private const val DATABASE_NAME_LENGTH = 0x3C
        private const val ENCODING_OFFSET = 0x4C
        private const val ADDRESS_BOOK_ENTRY_SIZE = 16
        private const val MASTER_CODE_SIZE = 32

        private const val FLAG_FOLDER = 0x1000
        private const val FLAG_FOLDER_ONE_HOT = 0x1100
    }

    private class InvalidUsrCheatDatabaseException(reason: String) : Exception("Invalid usrcheat.dat file: $reason")

    private val codeFormat = HexFormat {
        upperCase = true
        bytes {
            bytesPerGroup = 4
            groupSeparator = " "
        }
    }

    override fun parseCheatDatabase(databaseStream: ProgressTrackerInputStream, parseListener: CheatDatabaseParserListener) {
        val streamReader = StreamReader(databaseStream)

        // Read and validate header
        val header = streamReader.readBytes(HEADER_SIZE)
        val magic = String(header, 0, MAGIC_STRING.length, Charsets.US_ASCII)
        if (magic != MAGIC_STRING) {
            throw InvalidUsrCheatDatabaseException("Magic string mismatch (got '$magic')")
        }

        val charset = decodeEncoding(header)
        val databaseName = readNullTerminatedString(header, DATABASE_NAME_OFFSET, DATABASE_NAME_LENGTH, charset).ifBlank { "usrcheat.dat" }
        val cheatDatabase = parseListener.onDatabaseParseStart(databaseName)

        // Read address book entries
        val gameEntries = readAddressBook(streamReader)

        // Sort entries by offset so we can read them sequentially
        val sortedEntries = gameEntries.sortedBy { it.offset }

        for (entry in sortedEntries) {
            // Skip to the game's offset
            val toSkip = entry.offset - streamReader.bytesRead
            if (toSkip > 0) {
                streamReader.skip(toSkip)
            } else if (toSkip < 0) {
                // Shouldn't happen
                continue
            }

            val game = parseGame(streamReader, entry, cheatDatabase, charset, parseListener)
            if (game != null) {
                parseListener.onGameParsed(game)
            }
        }

        parseListener.onParseComplete()
    }

    private fun decodeEncoding(header: ByteArray): Charset {
        val b1 = header[ENCODING_OFFSET].toInt() and 0xFF
        val b2 = header[ENCODING_OFFSET + 1].toInt() and 0xFF
        val encodingWord = (b2 shl 8) or b1

        return when (encodingWord) {
            0x53D5 -> Charset.forName("GBK")
            0x53F5 -> Charset.forName("Big5")
            0x5375 -> Charset.forName("Shift_JIS")
            0x7355 -> Charsets.UTF_8
            else -> Charsets.UTF_8
        }
    }

    private fun readAddressBook(stream: StreamReader): List<GameEntry> {
        val entries = mutableListOf<GameEntry>()

        val addressBookEntry = ByteArray(ADDRESS_BOOK_ENTRY_SIZE)
        val buffer = ByteBuffer.wrap(addressBookEntry).order(ByteOrder.LITTLE_ENDIAN)

        while (true) {
            buffer.position(0)
            if (stream.read(addressBookEntry) != ADDRESS_BOOK_ENTRY_SIZE) {
                throw InvalidUsrCheatDatabaseException("Invalid address book entry")
            }

            val gameCodeBytes = ByteArray(4)
            buffer.get(gameCodeBytes)
            val crc = buffer.getInt()
            val offset = buffer.getInt()
            buffer.getInt()

            // Zero block terminates the address book
            if (gameCodeBytes.all { it == 0.toByte() } && crc == 0 && offset == 0) {
                break
            }

            val gameCode = String(gameCodeBytes, Charsets.US_ASCII)
            val checksum = String.format("%08X", crc)
            entries.add(GameEntry(gameCode, checksum, offset))
        }

        return entries
    }

    private fun parseGame(reader: StreamReader, entry: GameEntry, cheatDatabase: CheatDatabase, charset: Charset, parseListener: CheatDatabaseParserListener): Game? {
        val gameName = reader.readPaddedString(charset)
        if (gameName.isBlank()) {
            return null
        }

        parseListener.onGameParseStart(gameName)

        // Read number of items (2 bytes) + master code enable flag (2 bytes)
        val itemHeader = reader.readBytes(4)
        val numItems = readU16LE(itemHeader, 0)

        // Skip master code (32 bytes)
        reader.skip(MASTER_CODE_SIZE)

        // Parse items
        val folders = mutableListOf<CheatFolder>()
        val rootCheats = mutableListOf<Cheat>()
        var itemsParsed = 0

        while (itemsParsed < numItems) {
            val itemBytes = reader.readBytes(4)
            val itemCountOrSize = readU16LE(itemBytes, 0)
            val flags = readU16LE(itemBytes, 2)

            if (isFolder(flags)) {
                val folderName = reader.readNameAndDescription(charset).first
                val folderCheats = mutableListOf<Cheat>()

                repeat(itemCountOrSize) {
                    itemsParsed++
                    reader.skip(4)
                    val cheat = parseCheat(reader, cheatDatabase, charset)
                    if (cheat != null) {
                        folderCheats.add(cheat)
                    }
                }

                if (folderCheats.isNotEmpty()) {
                    folders.add(CheatFolder(null, folderName, folderCheats))
                }
            } else {
                val cheat = parseCheat(reader, cheatDatabase, charset)
                if (cheat != null) {
                    rootCheats.add(cheat)
                }
            }

            itemsParsed++
        }

        // Place root-level cheats into a default folder
        if (rootCheats.isNotEmpty()) {
            folders.add(0, CheatFolder(null, gameName, rootCheats))
        }

        if (folders.isEmpty()) {
            return null
        }

        return Game(null, gameName, entry.gameCode, entry.checksum, folders)
    }

    private fun isFolder(flags: Int): Boolean {
        return flags == FLAG_FOLDER || flags == FLAG_FOLDER_ONE_HOT
    }

    private fun parseCheat(reader: StreamReader, cheatDatabase: CheatDatabase, charset: Charset): Cheat? {
        val (cheatName, cheatDescription) = reader.readNameAndDescription(charset)

        if (cheatName.isBlank()) {
            return null
        }

        // Read code chunk count (4 bytes, little-endian)
        val countBytes = reader.readBytes(4)
        val codeChunkCount = readI32LE(countBytes, 0)
        val codeBytes = ByteArray(codeChunkCount * 4)
        reader.read(codeBytes)
        // Convert little endian to big endian
        for (i in 0 until codeChunkCount) {
            val index = i * 4
            val byte0 = codeBytes[index]
            val byte1 = codeBytes[index + 1]

            codeBytes[index] = codeBytes[index + 3]
            codeBytes[index + 1] = codeBytes[index + 2]
            codeBytes[index + 2] = byte1
            codeBytes[index + 3] = byte0
        }
        val code = codeBytes.toHexString(codeFormat)

        return Cheat(null, cheatDatabase.id ?: 0, cheatName, cheatDescription, code, false)
    }

    private fun readU16LE(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readI32LE(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readNullTerminatedString(data: ByteArray, offset: Int, maxLength: Int, charset: Charset): String {
        var end = offset
        val limit = (offset + maxLength).coerceAtMost(data.size)
        while (end < limit && data[end] != 0.toByte()) {
            end++
        }
        return String(data, offset, end - offset, charset)
    }

    private data class GameEntry(val gameCode: String, val checksum: String, val offset: Int)

    private class StreamReader(private val stream: ProgressTrackerInputStream) {

        private val singleByteBuffer = ByteArray(1)
        private val skipBuffer = ByteArray(1024)

        val bytesRead get() = stream.totalReadBytes

        fun readBytes(count: Int): ByteArray {
            val buffer = ByteArray(count)
            var totalRead = 0
            while (totalRead < count) {
                val read = stream.read(buffer, totalRead, count - totalRead)
                if (read == -1) throw InvalidUsrCheatDatabaseException("Unexpected end of stream at position $bytesRead")
                totalRead += read
            }
            return buffer
        }

        fun read(buffer: ByteArray): Int {
            return stream.read(buffer)
        }

        fun skip(count: Int) {
            var remaining = count
            do {
                val toRead = min(remaining, skipBuffer.size)
                val read = stream.read(skipBuffer, 0, toRead)
                if (read == 0) {
                    // skip() returned 0, try reading a byte to see if the stream has stalled, or we have reached end of stream
                    readOneByte()
                    remaining--
                } else {
                    remaining -= read
                }
            } while (remaining > 0)
        }

        fun readPaddedString(charset: Charset): String {
            val bytes = mutableListOf<Byte>()
            while (true) {
                val b = readOneByte()
                if (b == 0.toByte()) break
                bytes.add(b)
            }
            // Pad to 4-byte boundary: the total consumed is (string length + 1 for null).
            // We need to advance to the next 4-byte aligned position.
            val consumed = bytes.size + 1
            val padding = if (consumed % 4 == 0) 0 else 4 - (consumed % 4)
            if (padding > 0) {
                readBytes(padding)
            }
            return String(bytes.toByteArray(), charset)
        }

        /**
         * Name and description are 2 sequential strings, each one ending with a null terminator. The number of read bytes is equal to the length of the name and description
         * plus 2 for the null terminators, rounded up to the nearest multiple of 4.
         */
        fun readNameAndDescription(charset: Charset): Pair<String, String?> {
            // Read name until null
            val nameBytes = mutableListOf<Byte>()
            while (true) {
                val b = readOneByte()
                if (b == 0.toByte()) break
                nameBytes.add(b)
            }

            // Read description until null
            val descBytes = mutableListOf<Byte>()
            while (true) {
                val b = readOneByte()
                if (b == 0.toByte()) break
                descBytes.add(b)
            }

            val blockSize = nameBytes.size + 1 + descBytes.size + 1
            val padding = if (blockSize % 4 == 0) 0 else 4 - (blockSize % 4)
            if (padding > 0) {
                readBytes(padding)
            }

            val name = String(nameBytes.toByteArray(), charset)
            val description = if (descBytes.isNotEmpty()) String(descBytes.toByteArray(), charset) else null
            return Pair(name, description)
        }

        private fun readOneByte(): Byte {
            if (stream.read(singleByteBuffer) == -1) {
                throw InvalidUsrCheatDatabaseException("Unexpected end of stream at position $bytesRead")
            }
            return singleByteBuffer[0]
        }
    }
}