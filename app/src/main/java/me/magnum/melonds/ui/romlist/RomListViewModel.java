package me.magnum.melonds.ui.romlist;

import android.arch.lifecycle.ViewModel;
import io.reactivex.Observable;
import me.magnum.melonds.model.Rom;
import me.magnum.melonds.model.RomConfig;
import me.magnum.melonds.repositories.RomsRepository;

public class RomListViewModel extends ViewModel {
	private RomsRepository romsRepository;

	public RomListViewModel(RomsRepository romsRepository) {
		this.romsRepository = romsRepository;
	}

	public Observable<Rom> getRoms(boolean clearCache) {
		return this.romsRepository.getRoms(clearCache);
	}

	public void updateRomConfig(Rom rom, RomConfig newConfig) {
        this.romsRepository.updateRomConfig(rom, newConfig);
    }

	@Override
	protected void onCleared() {
		super.onCleared();
	}
}
