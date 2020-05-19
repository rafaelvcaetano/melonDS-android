package me.magnum.melonds.model;

public class RomConfig {
    private boolean loadGbaCart;
    private String gbaCartPath;
    private String gbaSavePath;

    public RomConfig() {
        this(false, null, null);
    }

    public RomConfig(boolean loadGbaCart, String gbaCartPath, String gbaSavePath) {
        this.loadGbaCart = loadGbaCart;
        this.gbaCartPath = gbaCartPath;
        this.gbaSavePath = gbaSavePath;
    }

    public boolean loadGbaCart() {
        return loadGbaCart;
    }

    public void setLoadGbaCart(boolean loadGbaCart) {
        this.loadGbaCart = loadGbaCart;
    }

    public String getGbaCartPath() {
        return gbaCartPath;
    }

    public void setGbaCartPath(String gbaCartPath) {
        this.gbaCartPath = gbaCartPath;
    }

    public String getGbaSavePath() {
        return gbaSavePath;
    }

    public void setGbaSavePath(String gbaSavePath) {
        this.gbaSavePath = gbaSavePath;
    }

    @Override
    public RomConfig clone() {
        return new RomConfig(this.loadGbaCart, this.gbaCartPath, this.gbaSavePath);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RomConfig romConfig = (RomConfig) o;

        if (loadGbaCart != romConfig.loadGbaCart) return false;
        if (gbaCartPath != null ? !gbaCartPath.equals(romConfig.gbaCartPath) : romConfig.gbaCartPath != null)
            return false;
        return gbaSavePath != null ? gbaSavePath.equals(romConfig.gbaSavePath) : romConfig.gbaSavePath == null;
    }

    @Override
    public int hashCode() {
        int result = (loadGbaCart ? 1 : 0);
        result = 31 * result + (gbaCartPath != null ? gbaCartPath.hashCode() : 0);
        result = 31 * result + (gbaSavePath != null ? gbaSavePath.hashCode() : 0);
        return result;
    }
}
