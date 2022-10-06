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
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import kotlin.experimental.and
import kotlin.math.min

object RomProcessor {
	private val DSIWARE_CATEGORY = 0x00030004.toUInt()
	private const val KEY_ROM_NAME = "name"
	private const val KEY_ROM_IS_DSIWARE_TITLE = "isDsiWareTitle"

	fun getRomMetadata(inputStream: BufferedInputStream): RomMetadata {
		val romStreamProcessor = RomStreamDataProcessor().apply {
			registerProcessor(
				RomStreamDataProcessor.SectionProcessor.SubSectionProcessor(
					KEY_ROM_NAME,
					streamOffset = 0x68,
					processor = {
						val offsetData = ByteArray(4)
						inputStream.read(offsetData)
						val bannerOffset = byteArrayToInt(offsetData)
						bannerOffset + (0x0340 - 4 * 2).toLong()
					},
					valueProcessor = {
						val titleData = ByteArray(128)
						inputStream.read(titleData)
						String(titleData, StandardCharsets.UTF_16LE)
							.trim()
							.substringBeforeLast('\n')
							.replace("\n", " ")
					}
				)
			)
			registerProcessor(
				RomStreamDataProcessor.SectionProcessor.SectionValueProcessor(
					KEY_ROM_IS_DSIWARE_TITLE,
					streamOffset = 0x230,
					processor = {
						val categoryData = ByteArray(4)
						inputStream.read(categoryData)
						val categoryId = byteArrayToInt(categoryData)
						categoryId.toUInt() == DSIWARE_CATEGORY
					}
				)
			)
			process(inputStream)
		}

		val romName = romStreamProcessor.getValue<String>(KEY_ROM_NAME)
		val isDsiWareTitle = romStreamProcessor.getValue<Boolean>(KEY_ROM_IS_DSIWARE_TITLE)

		return RomMetadata(
			romName,
			isDsiWareTitle,
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

	private fun byteArrayToInt(intData: ByteArray): Int {
		// NDS is little endian. Reorder bytes as needed
		// Also make sure that every byte is treated as an unsigned integer
		return  (intData[0].toInt() and 0xFF) or
				(intData[1].toInt() and 0xFF).shl(8) or
				(intData[2].toInt() and 0xFF).shl(16) or
				(intData[3].toInt() and 0xFF).shl(24)
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
					val value = processor.processor(trackedStream)
					values[processor.key] = value
				} else if (processor is SectionProcessor.SubSectionProcessor) {
					val newOffset = processor.processor(trackedStream)
					sortedProcessors.add(SectionProcessor.SectionValueProcessor(processor.key, newOffset, processor.valueProcessor))
					sortedProcessors.sortBy { it.streamOffset }
				}
			}
		}

		@Suppress("UNCHECKED_CAST")
		fun <T> getValue(key: String): T {
			return values[key] as T
		}

		sealed class SectionProcessor(val streamOffset: Long) {
			class SectionValueProcessor(val key: String, streamOffset: Long, val processor: (InputStream) -> Any) : SectionProcessor(streamOffset)
			class SubSectionProcessor(val key: String, streamOffset: Long, val processor: (InputStream) -> Long, val valueProcessor: (InputStream) -> Any) : SectionProcessor(streamOffset)
		}
	}
}
