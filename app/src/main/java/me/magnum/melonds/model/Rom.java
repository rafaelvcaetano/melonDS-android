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

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Rom rom = (Rom) o;

		return path.equals(rom.path);
	}

	@Override
	public int hashCode() {
		return path.hashCode();
	}
}
