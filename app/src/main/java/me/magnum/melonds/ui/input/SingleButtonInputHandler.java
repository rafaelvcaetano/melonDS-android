package me.magnum.melonds.ui.input;

import android.view.MotionEvent;
import android.view.View;

import me.magnum.melonds.IInputListener;
import me.magnum.melonds.MelonEmulator;
import me.magnum.melonds.model.Input;

public class SingleButtonInputHandler extends BaseInputHandler {
	private Input input;

	public SingleButtonInputHandler(IInputListener inputListener, Input input) {
		super(inputListener);
		this.input = input;
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				this.inputListener.onKeyPress(this.input);
				break;
			case MotionEvent.ACTION_UP:
				this.inputListener.onKeyReleased(this.input);
				break;
		}
		return true;
	}
}
