package me.magnum.melonds.utils

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import me.magnum.melonds.common.Crc32
import me.magnum.melonds.common.cheats.ProgressTrackerInputStream
import me.magnum.melonds.domain.model.RomInfo
import me.magnum.melonds.domain.model.RomMetadata
import me.magnum.melonds.domain.model.rom.Rom
import java.io.InputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.experimental.and
import kotlin.math.min

object RomProcessor {
	internal const val MAX_METADATA_STREAM_BYTES = 64L * 1024L * 1024L
	private val DSIWARE_CATEGORY = 0x00030004.toUInt()
	private const val KEY_ROM_NAME = "name"
	private const val KEY_DEVELOPER_NAME = "developer"
	private const val KEY_ROM_IS_DSIWARE_TITLE = "isDsiWareTitle"
	private const val KEY_ARM9_BOOTCODE = "arm9Bootcode"
	private const val KEY_ARM7_BOOTCODE = "arm7Bootcode"
	private const val KEY_HEADER = "header"
	private const val KEY_BANNER = "banner"

	@Suppress("NAME_SHADOWING")
	fun getRomMetadata(inputStream: InputStream): RomMetadata? {
		val romStreamProcessor = RomStreamDataProcessor().apply {
			registerSequentialProcessor(0x0) {
				val header = ByteArray(0x160)
				if (!stream.readFully(header)) fail()
				save(KEY_HEADER, header)

				val gameCode = String(header, 0x0C, 4)

				val arm9Offset = byteArrayToInt(header, 0x20)
				val arm9Size = byteArrayToInt(header, 0x2C)
				if (arm9Size !in 0..0x3BFE00 || !metadataSpanFits(arm9Offset, arm9Size)) fail()

				val arm7Offset = byteArrayToInt(header, 0x30)
				val arm7Size = byteArrayToInt(header, 0x3C)
				if (arm7Size !in 0..0x3BFE00 || !metadataSpanFits(arm7Offset, arm7Size)) fail()

				val bannerOffset = byteArrayToInt(header, 0x68)
				if (!metadataSpanFits(bannerOffset, 0xA00)) fail()

				registerValueProcessor(arm9Offset.toLong()) {
					val arm9BootCode = ByteArray(arm9Size)
					if (!stream.readFully(arm9BootCode)) fail()
					save(KEY_ARM9_BOOTCODE, arm9BootCode)
				}

				registerValueProcessor(arm7Offset.toLong()) {
					val arm7BootCode = ByteArray(arm7Size)
					if (!stream.readFully(arm7BootCode)) fail()
					save(KEY_ARM7_BOOTCODE, arm7BootCode)
				}

				registerValueProcessor(bannerOffset.toLong()) {
					val banner = ByteArray(0xA00)
					if (!stream.readFully(banner)) fail()
					save(KEY_BANNER, banner)

					val titleData = banner.copyOfRange(0x340, 0x340 + 256)
					val titleString = String(titleData, StandardCharsets.UTF_16LE).trim().replace("\u0000", "")

					val title = titleString.substringBeforeLast('\n').replace("\n", " ")
					val developer = titleString.substringAfterLast('\n')

					save(KEY_ROM_NAME, title)
					save(KEY_DEVELOPER_NAME, developer)
				}

				val cartCategory = gameCode[0]
				if (cartCategory == 'H' || cartCategory == 'K') {
					// This is probably a DSi Ware game. But confirm in a later value processor
					registerValueProcessor(0x234) {
						val categoryData = ByteArray(4)
						if (!stream.readFully(categoryData)) fail()
						val categoryId = byteArrayToInt(categoryData)
						save(KEY_ROM_IS_DSIWARE_TITLE, categoryId.toUInt() == DSIWARE_CATEGORY)
					}
				} else {
					save(KEY_ROM_IS_DSIWARE_TITLE, false)
				}
			}

			if (!process(inputStream)) {
				return null
			}
		}

		val romName = romStreamProcessor.getValue<String>(KEY_ROM_NAME)
		val developerName = romStreamProcessor.getValue<String>(KEY_DEVELOPER_NAME)
		val isDsiWareTitle = romStreamProcessor.getValue<Boolean>(KEY_ROM_IS_DSIWARE_TITLE)

		val header = romStreamProcessor.getValue<ByteArray>(KEY_HEADER)
		val arm9Bootcode = romStreamProcessor.getValue<ByteArray>(KEY_ARM9_BOOTCODE)
		val arm7Bootcode = romStreamProcessor.getValue<ByteArray>(KEY_ARM7_BOOTCODE)
		val banner = romStreamProcessor.getValue<ByteArray>(KEY_BANNER)

		val retroAchievementsMd5Digest = MessageDigest.getInstance("MD5").run {
			update(header)
			update(arm9Bootcode)
			update(arm7Bootcode)
			update(banner)
			digest()
		}

		val retroAchievementsHash = BigInteger(1, retroAchievementsMd5Digest).toString(16).padStart(32, '0')

		return RomMetadata(
			romName,
			developerName,
			isDsiWareTitle,
			retroAchievementsHash,
		)
	}

	private fun metadataSpanFits(offset: Int, size: Int): Boolean {
		if (offset < 0 || size < 0) return false
		return offset.toLong() <= MAX_METADATA_STREAM_BYTES &&
			size.toLong() <= MAX_METADATA_STREAM_BYTES - offset.toLong()
	}

	fun getRomIcon(inputStream: InputStream): Bitmap {
		val trackedStream = ProgressTrackerInputStream(inputStream).apply {
			setMaximumReadBytes(MAX_METADATA_STREAM_BYTES)
		}
		// Banner offset is at header offset 0x68
		trackedStream.skipStreamBytes(0x68)
		// Obtain the banner offset
		val offsetData = ByteArray(4)
		if (!trackedStream.readFully(offsetData)) throw java.io.EOFException("ROM banner offset is truncated")

		val bannerOffset = byteArrayToInt(offsetData)
		if (!metadataSpanFits(bannerOffset, 32 + 512 + 32)) {
			throw java.io.IOException("ROM banner is outside the metadata budget")
		}
		trackedStream.skipStreamBytes(bannerOffset.toLong() + 32 - (0x68 + 4))
		val tileData = ByteArray(512)
		if (!trackedStream.readFully(tileData)) throw java.io.EOFException("ROM icon tiles are truncated")

		val paletteData = ByteArray(16 * 2)
		if (!trackedStream.readFully(paletteData)) throw java.io.EOFException("ROM icon palette is truncated")

		val palette = UShortArray(16)
		for (i in 0 until 16) {
			// Each palette color is 16 bits. Join pairs of bytes to create the correct color
			val lower = paletteData[i * 2]
			val upper = paletteData[(i * 2) + 1]

			val value = ((upper.toInt() and 0xFF).shl(8) or (lower.toInt() and 0xFF)).toUShort()
			palette[i] = value
		}

		val argbPalette = paletteToArgb(palette)
		val icon = processTiles(tileData, argbPalette)
		val bitmapData = iconToBitmapArray(icon)

		val bitmap = createBitmap(32, 32)
		bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bitmapData))
		return bitmap
	}

	fun getRomInfo(rom: Rom, inputStream: InputStream): RomInfo? {
		val romHeader = ByteArray(0x200)
		if (!inputStream.readFully(romHeader)) {
			return null
		}

		val gameTitle = romHeader.decodeToString(endIndex = 12)
		val gameCode = romHeader.decodeToString(startIndex = 12, endIndex = 12 + 4)
		val headerChecksum = Crc32.compute(romHeader)
		return RomInfo(gameCode, headerChecksum, gameTitle, rom.name)
	}

	private fun byteArrayToInt(intData: ByteArray, offset: Int = 0): Int {
		// NDS is little endian. Reorder bytes as needed
		// Also make sure that every byte is treated as an unsigned integer
		return  (intData[offset + 0].toInt() and 0xFF) or
				(intData[offset + 1].toInt() and 0xFF).shl(8) or
				(intData[offset + 2].toInt() and 0xFF).shl(16) or
				(intData[offset + 3].toInt() and 0xFF).shl(24)
	}

	private fun paletteToArgb(palette: UShortArray): IntArray {
		val argbPalette = IntArray(16)
		for (i in 0 until 16) {
			val color = palette[i]

			val red =   getColor(color, 0).toInt() and 0xFF
			val green = getColor(color, 5).toInt() and 0xFF
			val blue =  getColor(color, 10).toInt() and 0xFF

			val argbColor = Color.argb(if (i == 0) 0 else 255, red, green, blue)
			argbPalette[i] = argbColor
		}

		return argbPalette
	}

	private fun processTiles(tileData: ByteArray, palette: IntArray): IntArray {
		val image = IntArray(32 * 32)

		for (ty in 0 until 4) {
			for (tx in 0 until 4) {
				for (i in 0 until 32) {
					val data = tileData[(ty * 4 + tx) * 32 + i]
					val first = ((data and 0xF0.toByte()).toInt() and 0xFF).shr(4)
					val second = (data.toInt() and 0xF)

					val outputX = tx * 8 + (i % 4) * 2
					val outputY = ty * 8 + i / 4
					val finalPos = outputY * 32 + outputX

					if (second == 0)
						image[finalPos] = 0
					else
						image[finalPos] = palette[second]

					if (first == 0)
						image[finalPos + 1] = 0
					else
						image[finalPos + 1] = palette[first]
				}
			}
		}

		return image
	}

	private fun iconToBitmapArray(icon: IntArray): ByteArray {
		val bitmapArray = ByteArray(32 * 32 * 4)

		for (i in icon.indices) {
			val argbColor = icon[i]

			bitmapArray[i * 4] = (argbColor.shr(16) and 0xFF).toByte()
			bitmapArray[i * 4 + 1] = (argbColor.shr(8) and 0xFF).toByte()
			bitmapArray[i * 4 + 2] = (argbColor and 0xFF).toByte()
			bitmapArray[i * 4 + 3] = (argbColor.shr(24) and 0xFF).toByte()
		}

		return bitmapArray
	}

	private fun getColor(color: UShort, offset: Int): Byte {
		val rawColor = (getRawColor(color, offset).toInt() and 0xFF)
		return ((rawColor.shl(3) + rawColor.shr(2)) and 0xFF).toByte()
	}

	private fun getRawColor(color: UShort, offset: Int): Byte {
		// Fetch 5 bits at the given offset
		return (color.toInt().shr(offset) and 0x1F).toByte()
	}

	/**
	 * Custom made way to skip bytes in an input stream. When dealing with zipped files, the internal implementations (ZipInputStream and BufferedInputStream) don't work very
	 * well. This one seems to work when dealing with a BufferedInputStream
	 */
	private fun InputStream.skipStreamBytes(bytes: Long) {
		if (bytes < 0) throw java.io.IOException("ROM metadata sections overlap or move backwards")
		if (bytes == 0L) return
		val buffer = ByteArray(1024)
		var remaining = bytes
		do {
			val toRead = min(remaining, buffer.size.toLong())
			val read = this.read(buffer, 0, toRead.toInt())
			if (read <= 0) throw java.io.EOFException("ROM metadata section is truncated")
			remaining -= read
		} while (remaining > 0)
	}

	private fun InputStream.readFully(buffer: ByteArray): Boolean {
		var offset = 0
		while (offset < buffer.size) {
			val count = read(buffer, offset, buffer.size - offset)
			if (count <= 0) return false
			offset += count
		}
		return true
	}

	private class RomStreamDataProcessor {
		private val processors = mutableListOf<SectionProcessor>()
		private val values = mutableMapOf<String, Any>()

		private class ProcessorFailException : Throwable()

		private sealed class SectionProcessor(val streamOffset: Long) {
			class SectionValueProcessor(streamOffset: Long, val processor: ValueProcessor.() -> Unit) : SectionProcessor(streamOffset)
			class SequentialSectionProcessor(streamOffset: Long, val then: SequentialProcessor.() -> Unit) : SectionProcessor(streamOffset)
		}

		interface ValueProcessor {
			val stream: InputStream

			fun save(key: String, value: Any)
			fun fail(): Nothing
		}

		interface SequentialProcessor {
			val stream: InputStream

			fun register(processor: SectionProcessor)
			fun save(key: String, value: Any)
			fun fail(): Nothing
		}

		fun registerSequentialProcessor(streamOffset: Long, processor: SequentialProcessor.() -> Unit) {
			processors.add(SectionProcessor.SequentialSectionProcessor(streamOffset, processor))
		}

		fun registerValueProcessor(streamOffset: Long, processor: ValueProcessor.() -> Unit) {
			processors.add(SectionProcessor.SectionValueProcessor(streamOffset, processor))
		}

		fun process(stream: InputStream): Boolean {
			val trackedStream = ProgressTrackerInputStream(stream)
			trackedStream.setMaximumReadBytes(MAX_METADATA_STREAM_BYTES)
			processors.sortBy { it.streamOffset }

			while (processors.isNotEmpty()) {
				val processor = processors.removeAt(0)
				val bytesToSkip = processor.streamOffset - trackedStream.totalReadBytes
				trackedStream.skipStreamBytes(bytesToSkip)

				try {
					if (processor is SectionProcessor.SectionValueProcessor) {
						processor.processor(
							object : ValueProcessor {
								override val stream = trackedStream

								override fun save(key: String, value: Any) {
									values[key] = value
								}

								override fun fail(): Nothing {
									throw ProcessorFailException()
								}
							}
						)
					} else if (processor is SectionProcessor.SequentialSectionProcessor) {
						processor.then(
							object : SequentialProcessor {
								override val stream = trackedStream

								override fun register(processor: SectionProcessor) {
									processors.add(processor)
								}

								override fun save(key: String, value: Any) {
									values[key] = value
								}

								override fun fail(): Nothing {
									throw ProcessorFailException()
								}
							}
						)

						processors.sortBy { it.streamOffset }
					}
				} catch (_: ProcessorFailException) {
					return false
				} catch (_: java.io.IOException) {
					return false
				}
			}

			return true
		}

		@Suppress("UNCHECKED_CAST")
		fun <T> getValue(key: String): T {
			return values[key] as T
		}
	}
}
