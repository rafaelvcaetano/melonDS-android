package me.magnum.melonds.utils;

import android.graphics.Bitmap;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class RomProcessor {
	public static String getRomName(File romFile) throws Exception {
		if (romFile == null)
			throw new NullPointerException("ROM file cannot be null");

		if (!romFile.isFile())
			throw new IllegalArgumentException("Argument must represent a file");

		FileInputStream fis = new FileInputStream(romFile);
		// Banner offset is at header offset 0x68
		fis.skip(0x68);
		// Obtain the banner offset
		byte[] offsetData = new byte[4];
		fis.read(offsetData);

		int bannerOffset = byteArrayToInt(offsetData);
		fis.skip(bannerOffset + 576 - (0x68 + 4));
		byte[] titleData = new byte[128];
		fis.read(titleData);
		return new String(titleData, Charset.forName("UTF-16LE"))
				.trim()
				.replace("\nNintendo", "")
				.replace("\n", " ");
	}

	public static Bitmap getRomIcon(File romFile) throws Exception {
		if (romFile == null)
			throw new NullPointerException("ROM file cannot be null");

		if (!romFile.isFile())
			throw new IllegalArgumentException("Argument must represent a file");

		FileInputStream fis = new FileInputStream(romFile);
		// Banner offset is at header offset 0x68
		fis.skip(0x68);
		// Obtain the banner offset
		byte[] offsetData = new byte[4];
		fis.read(offsetData);

		int bannerOffset = byteArrayToInt(offsetData);
		fis.skip(bannerOffset + 32 - (0x68 + 4));
		byte[] tileData = new byte[512];
		fis.read(tileData);

		byte[] paletteData = new byte[16 * 2];
		fis.read(paletteData);
		short[] palette = new short[16];
		for (int i = 0; i < 16; i++) {
			// Each palette color is 16 bits. Join pairs of bytes to create the correct color
			short lower = (short) (paletteData[i * 2] & 0xFF);
			short upper = (short) (paletteData[(i * 2) + 1] & 0xFF);

			short value = (short) ((lower | (upper << 8)) & 0xFFFF);
			palette[i] = value;
		}

		short[] icon = processTiles(tileData, palette);
		byte[] bitmapData = iconToBitmapArray(icon);

		Bitmap bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
		bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bitmapData));
		return bitmap;
	}

	private static int byteArrayToInt(byte[] intData) {
		// NDS is little endian. Reorder bytes as needed
		return (int) intData[0] | (intData[1] << 8) | (intData[2] << 16) | (intData[3] << 24);
	}

	private static short[] processTiles(byte[] tileData, short[] palette) {
		short[] image = new short[32 * 32];

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

	private static byte[] iconToBitmapArray(short[] icon) {
		byte[] bitmapArray = new byte[32 * 32 * 4];

		for (int i = 0; i < icon.length; i++) {
			short color = icon[i];

			byte red =   getColor(color, 0);
			byte green = getColor(color, 5);
			byte blue =  getColor(color, 10);

			bitmapArray[i * 4] = red;
			bitmapArray[i * 4 + 1] = green;
			bitmapArray[i * 4 + 2] = blue;
			bitmapArray[i * 4 + 3] = color == 0 ? 0 : (byte) 255;
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
