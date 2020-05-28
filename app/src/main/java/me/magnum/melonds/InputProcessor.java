package me.magnum.melonds;

import android.view.KeyEvent;

import me.magnum.melonds.model.ControllerConfiguration;
import me.magnum.melonds.model.Input;
import me.magnum.melonds.ui.input.IInputListener;
import me.magnum.melonds.ui.input.INativeInputListener;

public class InputProcessor implements INativeInputListener {
	private ControllerConfiguration controllerConfiguration;
	private IInputListener systemInputListener;
	private IInputListener frontendInputListener;

	public InputProcessor(ControllerConfiguration controllerConfiguration, IInputListener systemInputListener, IInputListener frontendInputListener) {
		this.controllerConfiguration = controllerConfiguration;
		this.systemInputListener = systemInputListener;
		this.frontendInputListener = frontendInputListener;
	}

	@Override
	public boolean onKeyEvent(KeyEvent keyEvent) {
		Input input = this.controllerConfiguration.keyToInput(keyEvent.getKeyCode());
		if (input == null)
			return false;

		if (input.isSystemInput()) {
			switch (keyEvent.getAction()) {
				case KeyEvent.ACTION_DOWN:
					this.systemInputListener.onKeyPress(input);
					return true;
				case KeyEvent.ACTION_UP:
					this.systemInputListener.onKeyReleased(input);
					return true;
			}
		} else {
			switch (keyEvent.getAction()) {
				case KeyEvent.ACTION_DOWN:
					this.frontendInputListener.onKeyPress(input);
					return true;
				case KeyEvent.ACTION_UP:
					this.frontendInputListener.onKeyReleased(input);
					return true;
			}
		}
		return false;
	}
}
