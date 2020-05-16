package me.magnum.melonds.ui.input;

import android.view.MotionEvent;
import android.view.View;

import me.magnum.melonds.IInputListener;
import me.magnum.melonds.model.Input;

public class ButtonsInputHandler extends BaseInputHandler {
	public ButtonsInputHandler(IInputListener inputListener) {
		super(inputListener);
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		float relativeX = event.getX() / v.getWidth();
		float relativeY = event.getY() / v.getHeight();

		this.inputListener.onKeyReleased(Input.A);
		this.inputListener.onKeyReleased(Input.B);
		this.inputListener.onKeyReleased(Input.X);
		this.inputListener.onKeyReleased(Input.Y);
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
			case MotionEvent.ACTION_MOVE:
				if (relativeX < 0.32f) {
					if (relativeY < 0.32f) {
						this.inputListener.onKeyPress(Input.X);
						this.inputListener.onKeyPress(Input.Y);
					} else if (relativeY < 0.68f) {
						this.inputListener.onKeyPress(Input.Y);
					} else {
						this.inputListener.onKeyPress(Input.Y);
						this.inputListener.onKeyPress(Input.B);
					}
				} else if (relativeX < 0.68f) {
					if (relativeY < 0.32f) {
						this.inputListener.onKeyPress(Input.X);
					} else if (relativeY > 0.68f) {
						this.inputListener.onKeyPress(Input.B);
					}
				} else {
					if (relativeY < 0.32f) {
						this.inputListener.onKeyPress(Input.X);
						this.inputListener.onKeyPress(Input.A);
					} else if (relativeY < 0.68f) {
						this.inputListener.onKeyPress(Input.A);
					} else {
						this.inputListener.onKeyPress(Input.A);
						this.inputListener.onKeyPress(Input.B);
					}
				}
				break;
		}

		return true;
	}
}
