package me.magnum.melonds.utils

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import me.magnum.melonds.common.Crc32
import me.magnum.melonds.common.cheats.ProgressTrackerInputStream
import me.magnum.melonds.domain.model.RomInfo
import me.magnum.melonds.domain.model.RomMetadata
import java.io.BufferedInputStream
import java.io.InputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.experimental.and
import kotlin.math.min

object RomProcessor {
	private val DSIWARE_CATEGORY = 0x00030004.toUInt()
	private const val KEY_ROM_NAME = "name"
	private const val KEY_DEVELOPER_NAME = "developer"
	private const val KEY_ROM_IS_DSIWARE_TITLE = "isDsiWareTitle"
	private const val KEY_ARM9_BOOTCODE = "arm9Bootcode"
	private const val KEY_ARM7_BOOTCODE = "arm7Bootcode"
	private const val KEY_HEADER = "header"
	private const val KEY_BANNER = "banner"

	@Suppress("NAME_SHADOWING")
	fun getRomMetadata(inputStream: BufferedInputStream): RomMetadata {
		val romStreamProcessor = RomStreamDataProcessor().apply {
			registerProcessor(
				RomStreamDataProcessor.SectionProcessor.SequentialSectionProcessor(
					streamOffset = 0x0,
					then = { stream, register, save ->
						val header = ByteArray(0x160)
						stream.read(header)
						save(KEY_HEADER, header)

						val gameCode = String(header, 0x0C, 4)

						val arm9Offset = byteArrayToInt(header, 0x20)
						val arm9Size = byteArrayToInt(header, 0x2C)

						val arm7Offset = byteArrayToInt(header, 0x30)
						val arm7Size = byteArrayToInt(header, 0x3C)

						val bannerOffset = byteArrayToInt(header, 0x68)

						val arm9Processor = RomStreamDataProcessor.SectionProcessor.SectionValueProcessor(
							streamOffset = arm9Offset.toLong()
						) { stream, save ->
							val arm9BootCode = ByteArray(arm9Size)
							stream.read(arm9BootCode)
							save(KEY_ARM9_BOOTCODE, arm9BootCode)
						}

						val arm7Processor = RomStreamDataProcessor.SectionProcessor.SectionValueProcessor(
							streamOffset = arm7Offset.toLong()
						) { stream, save ->
							val arm7BootCode = ByteArray(arm7Size)
							stream.read(arm7BootCode)
							save(KEY_ARM7_BOOTCODE, arm7BootCode)
						}

						val bannerProcessor = RomStreamDataProcessor.SectionProcessor.SectionValueProcessor(
							streamOffset = bannerOffset.toLong(),
						) { stream, save ->
							val banner = ByteArray(0xA00)
							stream.read(banner)
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
							register(
								RomStreamDataProcessor.SectionProcessor.SectionValueProcessor(
									streamOffset = 0x234
								) { stream, save ->
									val categoryData = ByteArray(4)
									stream.read(categoryData)
									val categoryId = byteArrayToInt(categoryData)
									save(KEY_ROM_IS_DSIWARE_TITLE, categoryId.toUInt() == DSIWARE_CATEGORY)
								}
							)
						} else {
							save(KEY_ROM_IS_DSIWARE_TITLE, false)
						}

						register(arm9Processor)
						register(arm7Processor)
						register(bannerProcessor)
					}
				)
			)

			process(inputStream)
		}

		val romName = romStreamProcessor.getValue<String>(KEY_ROM_NAME)
		val developerName = romStreamProcessor.getValue<String>(KEY_DEVELOPER_NAME)
		val isDsiWareTitle = romStreamProcessor.getValue<Boolean>(KEY_ROM_IS_DSIWARE_TITLE)

		val header = romStreamProcessor.getValue<ByteArray>(KEY_HEADER)
		val arm9Bootcode = romStreamProcessor.getValue<ByteArray>(KEY_ARM9_BOOTCODE)
		val arm7Bootcode = romStreamProcessor.getValue<ByteArray>(KEY_ARM7_BOOTCODE)
		val banner = romStreamProcessor.getValue<ByteArray>(KEY_BANNER)

		val hashDataSize = header.size + arm9Bootcode.size + arm7Bootcode.size + banner.size
		val retroAchievementsHashData = ByteArray(hashDataSize).apply {
			header.copyInto(this)
			arm9Bootcode.copyInto(this, header.size)
			arm7Bootcode.copyInto(this, header.size + arm9Bootcode.size)
			banner.copyInto(this, header.size + arm9Bootcode.size + arm7Bootcode.size)
		}

		val messageDigest = MessageDigest.getInstance("MD5")
		val retroAchievemetnsHash = BigInteger(1, messageDigest.digest(retroAchievementsHashData)).toString(16).padStart(32, '0')

		return RomMetadata(
			romName,
			developerName,
			isDsiWareTitle,
			retroAchievemetnsHash,
		)
	}

	fun getRomIcon(inputStream: BufferedInputStream): Bitmap {
		// Banner offset is at header offset 0x68
		inputStream.skipStreamBytes(0x68)
		// Obtain the banner offset
		val offsetData = ByteArray(4)
		inputStream.read(offsetData)

		val bannerOffset = byteArrayToInt(offsetData)
		inputStream.skipStreamBytes(bannerOffset.toLong() + 32 - (0x68 + 4))
		val tileData = ByteArray(512)
		inputStream.read(tileData)

		val paletteData = ByteArray(16 * 2)
		inputStream.read(paletteData)

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

	fun getRomInfo(inputStream: InputStream): RomInfo? {
		val romHeader = ByteArray(0x200)
		if (inputStream.read(romHeader) < 0x200) {
			return null
		}

		val gameTitle = romHeader.decodeToString(endIndex = 12)
		val gameCode = romHeader.decodeToString(startIndex = 12, endIndex = 12 + 4)
		val headerChecksum = Crc32(romHeader)
		return RomInfo(gameCode, headerChecksum.value, gameTitle)
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
		val buffer = ByteArray(1024)
		var remaining = bytes
		do {
			val toRead = min(remaining, buffer.size.toLong())
			val read = this.read(buffer, 0, toRead.toInt())
			if (read <= 0) {
				break
			}
			remaining -= read
		} while (remaining > 0)
	}

	private class RomStreamDataProcessor {
		private val processors = mutableListOf<SectionProcessor>()
		private val values = mutableMapOf<String, Any>()

		fun registerProcessor(processor: SectionProcessor) {
			processors.add(processor)
		}

		fun process(stream: BufferedInputStream) {
			val trackedStream = ProgressTrackerInputStream(stream)
			val sortedProcessors = processors.sortedBy { it.streamOffset }.toMutableList()

			while (sortedProcessors.isNotEmpty()) {
				val processor = sortedProcessors.removeFirst()
				val bytesToSkip = processor.streamOffset - trackedStream.totalReadBytes
				trackedStream.skipStreamBytes(bytesToSkip)

				if (processor is SectionProcessor.SectionValueProcessor) {
					processor.processor(trackedStream) { key, value ->
						values[key] = value
					}
				} else if (processor is SectionProcessor.SequentialSectionProcessor) {
					processor.then(
						trackedStream,
						{ sortedProcessors.add(it) },
						{ key, value -> values[key] = value }
					)

					sortedProcessors.sortBy { it.streamOffset }
				}
			}
		}

		@Suppress("UNCHECKED_CAST")
		fun <T> getValue(key: String): T {
			return values[key] as T
		}

		sealed class SectionProcessor(val streamOffset: Long) {
			class SectionValueProcessor(streamOffset: Long, val processor: (InputStream, save: (String, Any) -> Unit) -> Unit) : SectionProcessor(streamOffset)
			class SequentialSectionProcessor(streamOffset: Long, val then: (stream: InputStream, register: (SectionProcessor) -> Unit, save: (String, Any) -> Unit) -> Unit) : SectionProcessor(streamOffset)
		}
	}
}
