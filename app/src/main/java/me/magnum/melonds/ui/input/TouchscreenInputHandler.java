package me.magnum.melonds.ui.input;

import android.view.MotionEvent;
import android.view.View;

import me.magnum.melonds.MelonEmulator;
import me.magnum.melonds.model.Input;

public class TouchscreenInputHandler implements View.OnTouchListener {
	@Override
	public boolean onTouch(View v, MotionEvent event) {
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				MelonEmulator.onInputDown(Input.TOUCHSCREEN);
			case MotionEvent.ACTION_MOVE:
				float x = event.getX();
				float y = event.getY();
				int dsX = (int) (x / v.getWidth() * 256);
				int dsY = (int) (y / v.getHeight() * 192);

				MelonEmulator.onScreenTouch(dsX, dsY);
				break;
			case MotionEvent.ACTION_UP:
				MelonEmulator.onInputUp(Input.TOUCHSCREEN);
				MelonEmulator.onScreenRelease();
				break;
		}
		return true;
	}
}
