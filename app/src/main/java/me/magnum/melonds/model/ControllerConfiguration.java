package me.magnum.melonds.model;

import java.util.Map;

public class ControllerConfiguration {
	private Map<Integer, Input> inputMapper;
	private float joystickDeadZone;

	public ControllerConfiguration(Map<Integer, Input> inputMapper, float joystickDeadZone) {
		this.inputMapper = inputMapper;
		this.joystickDeadZone = joystickDeadZone;
	}

	public Input keyToInput(int key) {
		return this.inputMapper.get(key);
	}

	public float getJoystickDeadZone() {
		return joystickDeadZone;
	}
}
