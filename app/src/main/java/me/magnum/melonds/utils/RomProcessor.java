package me.magnum.melonds.utils;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class RomProcessor {
	public static String getRomName(ContentResolver contentResolver, Uri romUri) throws Exception {
		try (InputStream inputStream = contentResolver.openInputStream(romUri)) {
			// Banner offset is at header offset 0x68
			inputStream.skip(0x68);
			// Obtain the banner offset
			byte[] offsetData = new byte[4];
			inputStream.read(offsetData);

			int bannerOffset = byteArrayToInt(offsetData);
			inputStream.skip(bannerOffset + 576 - (0x68 + 4));
			byte[] titleData = new byte[128];
			inputStream.read(titleData);
			return new String(titleData, StandardCharsets.UTF_16LE)
					.trim()
					.replaceFirst("\n.*?$", "")
					.replace("\n", " ");
		}
	}

	public static Bitmap getRomIcon(ContentResolver contentResolver, Uri romFile) throws Exception {
		if (romFile == null)
			throw new NullPointerException("ROM file cannot be null");

		try (InputStream inputStream = contentResolver.openInputStream(romFile)) {
			// Banner offset is at header offset 0x68
			inputStream.skip(0x68);
			// Obtain the banner offset
			byte[] offsetData = new byte[4];
			inputStream.read(offsetData);

			int bannerOffset = byteArrayToInt(offsetData);
			inputStream.skip(bannerOffset + 32 - (0x68 + 4));
			byte[] tileData = new byte[512];
			inputStream.read(tileData);

			byte[] paletteData = new byte[16 * 2];
			inputStream.read(paletteData);

			short[] palette = new short[16];
			for (int i = 0; i < 16; i++) {
				// Each palette color is 16 bits. Join pairs of bytes to create the correct color
				short lower = (short) (paletteData[i * 2] & 0xFF);
				short upper = (short) (paletteData[(i * 2) + 1] & 0xFF);

				short value = (short) ((lower | (upper << 8)) & 0xFFFF);
				palette[i] = value;
			}

			int[] argbPalette = paletteToArgb(palette);
			int[] icon = processTiles(tileData, argbPalette);
			byte[] bitmapData = iconToBitmapArray(icon);

			Bitmap bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
			bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bitmapData));
			return bitmap;
		}
	}

	private static int byteArrayToInt(byte[] intData) {
		// NDS is little endian. Reorder bytes as needed
		// Also make sure that every byte is treated as an unsigned integer
		return (intData[0] & 0xFF) | ((intData[1] & 0xFF) << 8) | ((intData[2] & 0xFF) << 16) | ((intData[3] & 0xFF) << 24);
	}

	private static int[] paletteToArgb(short[] palette) {
		int[] argbPalette = new int[16];
		for (int i = 0; i < 16; i++) {
			short color = palette[i];

			int red =   (int) (getColor(color, 0) & 0xFF);
			int green = (int) (getColor(color, 5) & 0xFF);
			int blue =  (int) (getColor(color, 10) & 0xFF);

			int argbColor = Color.argb(i == 0 ? 0 : 255, red, green, blue);
			argbPalette[i] = argbColor;
		}

		return argbPalette;
	}

	private static int[] processTiles(byte[] tileData, int[] palette) {
		int[] image = new int[32 * 32];

		for (int ty = 0; ty < 4; ty++) {
			for (int tx = 0; tx < 4; tx++) {
				for (int i = 0; i < 32; i++) {
					byte data = tileData[(ty * 4 + tx) * 32 + i];
					byte first = (byte) ((data & 0xF0) >>> 4);
					byte second = (byte) (data & 0xF);

					int outputX = tx * 8 + (i % 4) * 2;
					int outputY = ty * 8 + i / 4;
					int finalPos = outputY * 32 + outputX;

					if (second == 0)
						image[finalPos] = 0;
					else
						image[finalPos] = palette[second];

					if (first == 0)
						image[finalPos + 1] = 0;
					else
						image[finalPos + 1] = palette[first];
				}
			}
		}

		return image;
	}

	private static byte[] iconToBitmapArray(int[] icon) {
		byte[] bitmapArray = new byte[32 * 32 * 4];

		for (int i = 0; i < icon.length; i++) {
			int argbColor = icon[i];

			bitmapArray[i * 4] = (byte) ((argbColor >> 16) & 0xFF);
			bitmapArray[i * 4 + 1] = (byte) ((argbColor >> 8) & 0xFF);
			bitmapArray[i * 4 + 2] = (byte) (argbColor & 0xFF);
			bitmapArray[i * 4 + 3] = (byte) ((argbColor >> 24) & 0xFF);
		}
		return bitmapArray;
	}

	private static byte getColor(short color, int offset) {
		byte rawColor = getRawColor(color, offset);
		return (byte) ((((rawColor << 3) & 0xFF) + ((0xFF & rawColor) >>> 2) & 0xFF) & 0xFF);
	}

	private static byte getRawColor(short color, int offset) {
		int offsetedColor = (byte) ((0xFFFF & color) >>> offset) & 0xFF;
		return (byte) (offsetedColor & 0x1F);
	}
}
