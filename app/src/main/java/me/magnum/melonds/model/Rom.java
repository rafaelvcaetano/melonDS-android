package me.magnum.melonds.model;

public class Rom {
	private String name;
	private String path;

	public Rom(String name, String path) {
		this.name = name;
		this.path = path;
	}

	public String getName() {
		return this.name;
	}

	public String getPath() {
		return this.path;
	}
}
