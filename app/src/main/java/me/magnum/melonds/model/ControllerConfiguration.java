package me.magnum.melonds.model;

import java.util.ArrayList;
import java.util.List;

public class ControllerConfiguration {
	private static final Input[] configurableInput = {
			Input.A,
			Input.B,
			Input.X,
			Input.Y,
			Input.LEFT,
			Input.RIGHT,
			Input.UP,
			Input.DOWN,
			Input.L,
			Input.R,
			Input.START,
			Input.SELECT,
			Input.HINGE,
			Input.PAUSE,
			Input.FAST_FORWARD
	};

	public static ControllerConfiguration empty() {
		ArrayList<InputConfig> inputConfigs = new ArrayList<>();
		for (Input input : configurableInput) {
			inputConfigs.add(new InputConfig(input, -1));
		}

		return new ControllerConfiguration(inputConfigs);
	}

	private List<InputConfig> inputMapper;

	public ControllerConfiguration(List<InputConfig> inputMapper) {
		ArrayList<InputConfig> actualConfig = new ArrayList<>();

		for (Input input : configurableInput) {
			InputConfig inputConfig = null;
			for (InputConfig config : inputMapper) {
				if (config.getInput() == input) {
					inputConfig = config;
					break;
				}
			}

			if (inputConfig != null)
				actualConfig.add(inputConfig);
			else
				actualConfig.add(new InputConfig(input));
		}

		this.inputMapper = actualConfig;
	}

	public Input keyToInput(int key) {
		for (int i = 0; i < inputMapper.size(); i++) {
			if (inputMapper.get(i).getKey() == key)
				return inputMapper.get(i).getInput();
		}

		return null;
	}

	public List<InputConfig> getConfigList() {
		return this.inputMapper;
	}
}
