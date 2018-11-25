package me.magnum.melonds;

import java.nio.ByteBuffer;

public final class MelonEmulator {
	private MelonEmulator() {
	}

	public static native void setupEmulator(String configDir);

	public static native boolean loadRom(String romPath, String sramPath);

	public static native void startEmulation();

	public static native void copyFrameBuffer(ByteBuffer frameBufferDst);

	public static native int getFPS();

	public static native void pauseEmulation();

	public static native void resumeEmulation();

	public static native void stopEmulation();

	public static native void onScreenTouch(int x, int y);

	public static native void onScreenRelease();

	public static native void onKeyPress(int key);

	public static native void onKeyRelease(int key);
}
