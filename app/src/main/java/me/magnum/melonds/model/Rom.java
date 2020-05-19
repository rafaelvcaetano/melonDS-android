package me.magnum.melonds.model;

public class Rom {
	private String name;
	private String path;
	private RomConfig config;

	public Rom(String name, String path, RomConfig config) {
		this.name = name;
		this.path = path;
		this.config = config;
	}

	public String getName() {
		return this.name;
	}

	public String getPath() {
		return this.path;
	}

	public RomConfig getConfig() {
		return config;
	}

	public void setConfig(RomConfig config) {
		this.config = config;
	}
}
