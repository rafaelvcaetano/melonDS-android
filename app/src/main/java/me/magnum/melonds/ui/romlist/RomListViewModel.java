package me.magnum.melonds.ui.romlist;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import me.magnum.melonds.model.Rom;
import me.magnum.melonds.model.RomConfig;
import me.magnum.melonds.model.RomScanningStatus;
import me.magnum.melonds.repositories.RomsRepository;

import java.util.List;

public class RomListViewModel extends ViewModel {
	private RomsRepository romsRepository;

	private CompositeDisposable disposables;

	public RomListViewModel(RomsRepository romsRepository) {
		this.romsRepository = romsRepository;
		this.disposables = new CompositeDisposable();
	}

	public LiveData<List<Rom>> getRoms() {
		final MutableLiveData<List<Rom>> romsLiveData = new MutableLiveData<>();

		Disposable disposable = this.romsRepository.getRoms()
			.subscribe(new Consumer<List<Rom>>() {
				@Override
				public void accept(List<Rom> roms) {
					romsLiveData.postValue(roms);
				}
			});
		this.disposables.add(disposable);
		return romsLiveData;
	}

	public LiveData<RomScanningStatus> getRomScanningStatus() {
		final MutableLiveData<RomScanningStatus> scanningStatusLiveData = new MutableLiveData<>();

		Disposable disposable = this.romsRepository.getRomScanningStatus()
				.subscribe(new Consumer<RomScanningStatus>() {
					@Override
					public void accept(RomScanningStatus status) {
						scanningStatusLiveData.postValue(status);
					}
				});

		this.disposables.add(disposable);
		return scanningStatusLiveData;
	}

	public void refreshRoms() {
		this.romsRepository.rescanRoms();
	}

	public void updateRomConfig(Rom rom, RomConfig newConfig) {
        this.romsRepository.updateRomConfig(rom, newConfig);
    }

	@Override
	protected void onCleared() {
		super.onCleared();
		this.disposables.dispose();
	}
}
