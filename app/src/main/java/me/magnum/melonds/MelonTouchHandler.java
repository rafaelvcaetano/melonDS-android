package me.magnum.melonds;

import me.magnum.melonds.model.Input;
import me.magnum.melonds.model.Point;

public class MelonTouchHandler implements IInputListener {
	@Override
	public void onKeyPress(Input key) {
		MelonEmulator.onInputDown(key);
	}

	@Override
	public void onKeyReleased(Input key) {
		MelonEmulator.onInputUp(key);
	}

	@Override
	public void onTouch(Point point) {
		MelonEmulator.onScreenTouch(point.x, point.y);
	}
}
