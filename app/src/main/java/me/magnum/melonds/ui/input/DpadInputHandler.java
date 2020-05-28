package me.magnum.melonds.ui.input;

import android.view.MotionEvent;
import android.view.View;

import me.magnum.melonds.model.Input;

public class DpadInputHandler extends BaseInputHandler {
	public DpadInputHandler(IInputListener inputListener) {
		super(inputListener);
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		float relativeX = event.getX() / v.getWidth();
		float relativeY = event.getY() / v.getHeight();

		this.inputListener.onKeyReleased(Input.RIGHT);
		this.inputListener.onKeyReleased(Input.LEFT);
		this.inputListener.onKeyReleased(Input.UP);
		this.inputListener.onKeyReleased(Input.DOWN);
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
			case MotionEvent.ACTION_MOVE:
				if (relativeX < 0.32f) {
					if (relativeY < 0.32f) {
						this.inputListener.onKeyPress(Input.UP);
						this.inputListener.onKeyPress(Input.LEFT);
					} else if (relativeY < 0.68f) {
						this.inputListener.onKeyPress(Input.LEFT);
					} else {
						this.inputListener.onKeyPress(Input.DOWN);
						this.inputListener.onKeyPress(Input.LEFT);
					}
				} else if (relativeX < 0.68f) {
					if (relativeY < 0.32f) {
						this.inputListener.onKeyPress(Input.UP);
					} else if (relativeY > 0.68f) {
						this.inputListener.onKeyPress(Input.DOWN);
					}
				} else {
					if (relativeY < 0.32f) {
						this.inputListener.onKeyPress(Input.UP);
						this.inputListener.onKeyPress(Input.RIGHT);
					} else if (relativeY < 0.68f) {
						this.inputListener.onKeyPress(Input.RIGHT);
					} else {
						this.inputListener.onKeyPress(Input.DOWN);
						this.inputListener.onKeyPress(Input.RIGHT);
					}
				}
				break;
		}

		return true;
	}
}
