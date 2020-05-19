package me.magnum.melonds.parcelables;

import android.os.Parcel;
import android.os.Parcelable;
import me.magnum.melonds.model.Rom;
import me.magnum.melonds.model.RomConfig;

public class RomParcelable implements Parcelable {
    private Rom rom;

    public RomParcelable(Rom rom) {
        this.rom = rom;
    }

    protected RomParcelable(Parcel in) {
        RomConfig romConfig = new RomConfig();
        this.rom = new Rom(in.readString(), in.readString(), romConfig);
        romConfig.setLoadGbaCart(in.readInt() == 1);
        romConfig.setGbaCartPath(in.readString());
        romConfig.setGbaSavePath(in.readString());
    }

    public Rom getRom() {
        return rom;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(rom.getName());
        dest.writeString(rom.getPath());
        dest.writeInt(rom.getConfig().loadGbaCart() ? 1 : 0);
        dest.writeString(rom.getConfig().getGbaCartPath());
        dest.writeString(rom.getConfig().getGbaSavePath());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<RomParcelable> CREATOR = new Creator<RomParcelable>() {
        @Override
        public RomParcelable createFromParcel(Parcel in) {
            return new RomParcelable(in);
        }

        @Override
        public RomParcelable[] newArray(int size) {
            return new RomParcelable[size];
        }
    };
}
