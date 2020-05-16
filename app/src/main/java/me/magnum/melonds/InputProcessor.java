package me.magnum.melonds;

import android.view.KeyEvent;
import android.view.MotionEvent;

import me.magnum.melonds.model.ControllerConfiguration;
import me.magnum.melonds.model.Input;

public class InputProcessor implements INativeInputListener {
	private ControllerConfiguration controllerConfiguration;
	private IInputListener inputListener;

	public InputProcessor(ControllerConfiguration controllerConfiguration, IInputListener inputListener) {
		this.controllerConfiguration = controllerConfiguration;
		this.inputListener = inputListener;
	}

	@Override
	public boolean onMotionEvent(MotionEvent motionEvent) {
		return false;
	}

	@Override
	public boolean onKeyEvent(KeyEvent keyEvent) {
		Input input = this.controllerConfiguration.keyToInput(keyEvent.getKeyCode());
		if (input == null)
			return false;

		switch (keyEvent.getAction()) {
			case KeyEvent.ACTION_DOWN:
				this.inputListener.onKeyPress(input);
				return true;
			case KeyEvent.ACTION_UP:
				this.inputListener.onKeyReleased(input);
				return true;
		}
		return false;
	}
}
