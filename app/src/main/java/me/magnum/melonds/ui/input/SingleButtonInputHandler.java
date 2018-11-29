package me.magnum.melonds.ui.input;

import android.view.MotionEvent;
import android.view.View;

import me.magnum.melonds.MelonEmulator;
import me.magnum.melonds.model.Input;

public class SingleButtonInputHandler implements View.OnTouchListener {
	private Input input;

	public SingleButtonInputHandler(Input input) {
		this.input = input;
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				MelonEmulator.onInputDown(this.input);
				break;
			case MotionEvent.ACTION_UP:
				MelonEmulator.onInputUp(this.input);
				break;
		}
		return true;
	}
}
