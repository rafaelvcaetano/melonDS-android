package me.magnum.melonds.utils

import android.graphics.Bitmap
import android.graphics.Color
import me.magnum.melonds.common.Crc32
import me.magnum.melonds.domain.model.RomInfo
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import kotlin.experimental.and

object RomProcessor {
	fun getRomName(inputStream: InputStream): String {
		// Banner offset is at header offset 0x68
		inputStream.skip(0x68)
		// Obtain the banner offset
		val offsetData = ByteArray(4)
		inputStream.read(offsetData)

		val bannerOffset = byteArrayToInt(offsetData)
		inputStream.skip(bannerOffset.toLong() + 576 - (0x68 + 4))
		val titleData = ByteArray(128)
		inputStream.read(titleData)
		return String(titleData, StandardCharsets.UTF_16LE)
				.trim()
				.replaceFirst("\n.*?$".toRegex(), "")
				.replace("\n", " ")
	}

	fun getRomIcon(inputStream: InputStream): Bitmap {
		// Banner offset is at header offset 0x68
		inputStream.skip(0x68)
		// Obtain the banner offset
		val offsetData = ByteArray(4)
		inputStream.read(offsetData)

		val bannerOffset = byteArrayToInt(offsetData)
		inputStream.skip(bannerOffset.toLong() + 32 - (0x68 + 4))
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

		val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
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
}
