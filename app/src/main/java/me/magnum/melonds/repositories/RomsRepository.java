package me.magnum.melonds.repositories;

import io.reactivex.Observable;
import me.magnum.melonds.model.Rom;
import me.magnum.melonds.model.RomConfig;
import me.magnum.melonds.model.RomScanningStatus;

import java.util.List;

public interface RomsRepository {
    Observable<List<Rom>> getRoms();
    Observable<RomScanningStatus> getRomScanningStatus();

    void updateRomConfig(Rom rom, RomConfig romConfig);
    void rescanRoms();
}
