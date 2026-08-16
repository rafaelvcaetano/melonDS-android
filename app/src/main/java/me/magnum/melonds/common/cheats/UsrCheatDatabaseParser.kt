package me.magnum.melonds.common.cheats

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

        private const val THREAD_POOL_SIZE = 4
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

        // To reduce the time required to parse all the cheats, parsing will be done in a multithreaded context, with a producer-consumer setup. One coroutine, the producer,
        // reads game entries from the database's "address book". These entries are pushed into a Channel to be consumed by consumers. Each consumer is responsible for parsing
        // all cheat data of the game associated with that entry
        runBlocking {
            val channel = Channel<GameData>(capacity = THREAD_POOL_SIZE)

            // Producer: reads the stream sequentially and sends game data blocks into the channel
            launch(Dispatchers.IO) {
                for (i in sortedEntries.indices) {
                    val entry = sortedEntries[i]

                    // Skip to the game's offset
                    val toSkip = entry.offset - streamReader.bytesRead
                    if (toSkip > 0) {
                        streamReader.skip(toSkip)
                    } else if (toSkip < 0) {
                        // Shouldn't happen
                        continue
                    }

                    // Determine how many bytes to read for this game
                    val startOffset = streamReader.bytesRead
                    val endOffset = if (i < sortedEntries.size - 1) {
                        sortedEntries[i + 1].offset
                    } else {
                        // Last entry: read until end of stream (cap at 1MB safety limit)
                        startOffset + 1024 * 1024
                    }

                    val blockSize = endOffset - startOffset
                    val data = streamReader.readBytesOrUntilEnd(blockSize)
                    channel.send(GameData(entry, data))
                }
                channel.close()
            }

            // Consumers: fixed pool of coroutines parsing games concurrently.
            // The listener is notified immediately as each game is parsed.
            val workers = List(THREAD_POOL_SIZE) {
                launch(Dispatchers.Default) {
                    for (gameData in channel) {
                        val gameName = peekGameName(gameData.data, charset)
                        if (gameName != null) {
                            synchronized(parseListener) {
                                parseListener.onGameParseStart(gameName)
                            }
                        }
                        val game = parseGame(gameData.data, gameData.entry, cheatDatabase, charset)
                        if (game != null) {
                            synchronized(parseListener) {
                                parseListener.onGameParsed(game)
                            }
                        }
                    }
                }
            }

            // Wait for all workers to finish
            workers.joinAll()
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

    /**
     * Reads just the game name from the raw byte data without performing a full parse.
     */
    private fun peekGameName(data: ByteArray, charset: Charset): String? {
        val reader = ByteArrayReader(data)
        val gameName = reader.readPaddedString(charset)
        return gameName.ifBlank { null }
    }

    private fun parseGame(data: ByteArray, entry: GameEntry, cheatDatabase: CheatDatabase, charset: Charset): Game? {
        val reader = ByteArrayReader(data)

        val gameName = reader.readPaddedString(charset)
        if (gameName.isBlank()) {
            return null
        }

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

    private fun parseCheat(reader: ByteArrayReader, cheatDatabase: CheatDatabase, charset: Charset): Cheat? {
        val (cheatName, cheatDescription) = reader.readNameAndDescription(charset)

        if (cheatName.isBlank()) {
            return null
        }

        // Read code chunk count (4 bytes, little-endian)
        val countBytes = reader.readBytes(4)
        val codeChunkCount = readI32LE(countBytes, 0)
        val codeBytes = reader.readBytes(codeChunkCount * 4)
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
    private class GameData(val entry: GameEntry, val data: ByteArray)

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

        fun readBytesOrUntilEnd(maxCount: Int): ByteArray {
            val buffer = ByteArray(maxCount)
            var totalRead = 0
            while (totalRead < maxCount) {
                val read = stream.read(buffer, totalRead, maxCount - totalRead)
                if (read == -1) break
                totalRead += read
            }
            return if (totalRead == maxCount) buffer else buffer.copyOf(totalRead)
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

        private fun readOneByte(): Byte {
            if (stream.read(singleByteBuffer) == -1) {
                throw InvalidUsrCheatDatabaseException("Unexpected end of stream at position $bytesRead")
            }
            return singleByteBuffer[0]
        }
    }

    private class ByteArrayReader(private val data: ByteArray) {
        var position = 0
            private set

        fun readBytes(count: Int): ByteArray {
            if (position + count > data.size) {
                throw InvalidUsrCheatDatabaseException("Unexpected end of data at position $position (requested $count bytes, available ${data.size - position})")
            }
            val result = data.copyOfRange(position, position + count)
            position += count
            return result
        }

        fun skip(count: Int) {
            position += count
        }

        fun readPaddedString(charset: Charset): String {
            val bytes = mutableListOf<Byte>()
            while (position < data.size) {
                val b = data[position++]
                if (b == 0.toByte()) break
                bytes.add(b)
            }
            // Pad to 4-byte boundary: the total consumed is (string length + 1 for null).
            // We need to advance to the next 4-byte aligned position.
            val consumed = bytes.size + 1
            val padding = if (consumed % 4 == 0) 0 else 4 - (consumed % 4)
            position += padding
            return String(bytes.toByteArray(), charset)
        }

        fun readNameAndDescription(charset: Charset): Pair<String, String?> {
            // Read name until null
            val nameBytes = mutableListOf<Byte>()
            while (position < data.size) {
                val b = data[position++]
                if (b == 0.toByte()) break
                nameBytes.add(b)
            }

            // Read description until null
            val descBytes = mutableListOf<Byte>()
            while (position < data.size) {
                val b = data[position++]
                if (b == 0.toByte()) break
                descBytes.add(b)
            }

            val blockSize = nameBytes.size + 1 + descBytes.size + 1
            val padding = if (blockSize % 4 == 0) 0 else 4 - (blockSize % 4)
            position += padding

            val name = String(nameBytes.toByteArray(), charset)
            val description = if (descBytes.isNotEmpty()) String(descBytes.toByteArray(), charset) else null
            return Pair(name, description)
        }
    }
}