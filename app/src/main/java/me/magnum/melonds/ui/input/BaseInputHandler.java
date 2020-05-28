package me.magnum.melonds.ui.input;

import android.view.View;

public abstract class BaseInputHandler implements View.OnTouchListener {
	protected IInputListener inputListener;

	public BaseInputHandler(IInputListener inputListener) {
		this.inputListener = inputListener;
	}
}
