package me.magnum.melonds.ui.input;

import android.view.MotionEvent;
import android.view.View;

import me.magnum.melonds.MelonEmulator;
import me.magnum.melonds.model.Input;
import me.magnum.melonds.model.Point;

public class TouchscreenInputHandler extends BaseInputHandler {
	private Point touchPoint;

	public TouchscreenInputHandler(IInputListener inputListener) {
		super(inputListener);
		this.touchPoint = new Point();
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				this.inputListener.onKeyPress(Input.TOUCHSCREEN);
			case MotionEvent.ACTION_MOVE:
				float x = event.getX();
				float y = event.getY();
				touchPoint.x = (int) (x / v.getWidth() * 256);
				touchPoint.y = (int) (y / v.getHeight() * 192);

				this.inputListener.onTouch(this.touchPoint);
				break;
			case MotionEvent.ACTION_UP:
				this.inputListener.onKeyReleased(Input.TOUCHSCREEN);
				MelonEmulator.onScreenRelease();
				break;
		}
		return true;
	}
}
