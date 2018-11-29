package me.magnum.melonds.ui.input;

import android.view.MotionEvent;
import android.view.View;

import me.magnum.melonds.MelonEmulator;
import me.magnum.melonds.model.Input;

public class DpadInputHandler implements View.OnTouchListener {
	@Override
	public boolean onTouch(View v, MotionEvent event) {
		float relativeX = event.getX() / v.getWidth();
		float relativeY = event.getY() / v.getHeight();

		MelonEmulator.onInputUp(Input.RIGHT);
		MelonEmulator.onInputUp(Input.LEFT);
		MelonEmulator.onInputUp(Input.UP);
		MelonEmulator.onInputUp(Input.DOWN);
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
			case MotionEvent.ACTION_MOVE:
				if (relativeX < 0.32f) {
					if (relativeY < 0.32f) {
						MelonEmulator.onInputDown(Input.UP);
						MelonEmulator.onInputDown(Input.LEFT);
					} else if (relativeY < 0.68f) {
						MelonEmulator.onInputDown(Input.LEFT);
					} else {
						MelonEmulator.onInputDown(Input.DOWN);
						MelonEmulator.onInputDown(Input.LEFT);
					}
				} else if (relativeX < 0.68f) {
					if (relativeY < 0.32f) {
						MelonEmulator.onInputDown(Input.UP);
					} else if (relativeY > 0.68f) {
						MelonEmulator.onInputDown(Input.DOWN);
					}
				} else {
					if (relativeY < 0.32f) {
						MelonEmulator.onInputDown(Input.UP);
						MelonEmulator.onInputDown(Input.RIGHT);
					} else if (relativeY < 0.68f) {
						MelonEmulator.onInputDown(Input.RIGHT);
					} else {
						MelonEmulator.onInputDown(Input.DOWN);
						MelonEmulator.onInputDown(Input.RIGHT);
					}
				}
				break;
		}

		return true;
	}
}
