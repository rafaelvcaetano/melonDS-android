package me.magnum.melonds.ui.input;

import android.view.MotionEvent;
import android.view.View;

import me.magnum.melonds.MelonEmulator;
import me.magnum.melonds.model.Input;

public class ButtonsInputHandler implements View.OnTouchListener {
	@Override
	public boolean onTouch(View v, MotionEvent event) {
		float relativeX = event.getX() / v.getWidth();
		float relativeY = event.getY() / v.getHeight();

		MelonEmulator.onInputUp(Input.A);
		MelonEmulator.onInputUp(Input.B);
		MelonEmulator.onInputUp(Input.X);
		MelonEmulator.onInputUp(Input.Y);
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
			case MotionEvent.ACTION_MOVE:
				if (relativeX < 0.32f) {
					if (relativeY < 0.32f) {
						MelonEmulator.onInputDown(Input.X);
						MelonEmulator.onInputDown(Input.Y);
					} else if (relativeY < 0.68f) {
						MelonEmulator.onInputDown(Input.Y);
					} else {
						MelonEmulator.onInputDown(Input.Y);
						MelonEmulator.onInputDown(Input.B);
					}
				} else if (relativeX < 0.68f) {
					if (relativeY < 0.32f) {
						MelonEmulator.onInputDown(Input.X);
					} else if (relativeY > 0.68f) {
						MelonEmulator.onInputDown(Input.B);
					}
				} else {
					if (relativeY < 0.32f) {
						MelonEmulator.onInputDown(Input.X);
						MelonEmulator.onInputDown(Input.A);
					} else if (relativeY < 0.68f) {
						MelonEmulator.onInputDown(Input.A);
					} else {
						MelonEmulator.onInputDown(Input.A);
						MelonEmulator.onInputDown(Input.B);
					}
				}
				break;
		}

		return true;
	}
}
