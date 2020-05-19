package me.magnum.melonds;

import java.nio.ByteBuffer;

import me.magnum.melonds.model.Input;

public final class MelonEmulator {
	public enum LoadResult {
		SUCCESS,
		SUCCESS_GBA_FAILED,
		NDS_FAILED
	}

	private MelonEmulator() {
	}

	public static native void setupEmulator(String configDir);

	public static LoadResult loadRom(String romPath, String sramPath, boolean loadDirect, boolean loadGbaRom, String gbaRomPath, String gbaSramPath) {
		int loadResult = loadRomInternal(romPath, sramPath, loadDirect, loadGbaRom, gbaRomPath, gbaSramPath);

		switch (loadResult) {
			case 0:
				return LoadResult.SUCCESS;
			case 1:
				return LoadResult.SUCCESS_GBA_FAILED;
			case 2:
				return LoadResult.NDS_FAILED;
		}
		throw new RuntimeException("Unknown load result");
	}

	private static native int loadRomInternal(String romPath, String sramPath, boolean loadDirect, boolean loadGbaRom, String gbaRomPath, String gbaSramPath);

	public static native void startEmulation();

	public static native void copyFrameBuffer(ByteBuffer frameBufferDst);

	public static native int getFPS();

	public static native void pauseEmulation();

	public static native void resumeEmulation();

	public static native void stopEmulation();

	public static native void onScreenTouch(int x, int y);

	public static native void onScreenRelease();

	public static void onInputDown(Input input) {
		onKeyPress(input.getKeyCode());
	}

	public static void onInputUp(Input input) {
		onKeyRelease(input.getKeyCode());
	}

	private static native void onKeyPress(int key);

	private static native void onKeyRelease(int key);
}
