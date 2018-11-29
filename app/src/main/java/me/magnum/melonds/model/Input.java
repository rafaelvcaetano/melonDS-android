package me.magnum.melonds.model;

public enum Input {
	A(0),
	B(1),
	SELECT(2),
	START(3),
	RIGHT(4),
	LEFT(5),
	UP(6),
	DOWN(7),
	L(8),
	R(9),
	X(16 + 0),
	Y(17 + 1),
	DEBUG(16 + 3),
	TOUCHSCREEN(16 + 6),
	HINGE(16 + 7);

	private int keyCode;

	Input(int keyCode) {
		this.keyCode = keyCode;
	}

	public int getKeyCode() {
		return this.keyCode;
	}
}
