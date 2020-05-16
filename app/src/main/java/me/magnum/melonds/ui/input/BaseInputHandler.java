package me.magnum.melonds.ui.input;

import android.view.View;

import me.magnum.melonds.IInputListener;

public abstract class BaseInputHandler implements View.OnTouchListener {
	protected IInputListener inputListener;

	public BaseInputHandler(IInputListener inputListener) {
		this.inputListener = inputListener;
	}
}
