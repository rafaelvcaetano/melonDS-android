package me.magnum.melonds.repositories;

import io.reactivex.Observable;
import me.magnum.melonds.model.Rom;
import me.magnum.melonds.model.RomConfig;

public interface RomsRepository {
    Observable<Rom> getRoms(boolean force);
    void updateRomConfig(Rom rom, RomConfig romConfig);
}
