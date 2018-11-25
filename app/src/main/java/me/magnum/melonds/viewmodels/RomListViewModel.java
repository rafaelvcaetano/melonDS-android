package me.magnum.melonds.viewmodels;

import android.arch.lifecycle.ViewModel;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Single;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import me.magnum.melonds.IRomLocationCache;
import me.magnum.melonds.model.Rom;
import me.magnum.melonds.utils.RomProcessor;

import static io.reactivex.Observable.fromArray;

public class RomListViewModel extends ViewModel {
	private IRomLocationCache romLocationCache;

	public RomListViewModel(IRomLocationCache romLocationCache) {
		this.romLocationCache = romLocationCache;
	}

	public Observable<Rom> getRoms(boolean clearCache) {
		final List<Rom> roms = new ArrayList<>();
		Single<List<String>> initialListObservable;
		if (clearCache) {
			initialListObservable = Observable.fromArray()
					.cast(String.class)
					.toList();
		} else {
			initialListObservable = this.romLocationCache.getCachedRomPaths()
					.filter(new Predicate<String>() {
						@Override
						public boolean test(String s) {
							return new File(s).isFile();
						}
					})
					.toList();
		}

		return initialListObservable
				.flatMapObservable(new Function<List<String>, Observable<String>>() {
					@Override
					public Observable<String> apply(final List<String> cachedFiles) {
						return Observable.create(new ObservableOnSubscribe<String>() {
							private void findFiles(File directory, ObservableEmitter<String> emitter) {
								File[] files = directory.listFiles();
								if (files == null)
									return;

								for (File f : files) {
									if (f.isDirectory())
										findFiles(f, emitter);

									if (cachedFiles.contains(f.getAbsolutePath()))
										continue;

									String fileName = f.getName();
									if (fileName.endsWith(".nds"))
										emitter.onNext(f.getAbsolutePath());

									// TODO: support zip files
								}
							}

							@Override
							public void subscribe(ObservableEmitter<String> emitter) {
								findFiles(Environment.getExternalStorageDirectory(), emitter);
								emitter.onComplete();
							}
						}).startWith(cachedFiles);
					}
				})
				.map(new Function<String, Rom>() {
					@Override
					public Rom apply(String filePath) {
						try {
							String romName = RomProcessor.getRomName(new File(filePath));
							return new Rom(romName, filePath);
						} catch (Exception e) {
							e.printStackTrace();

							String fileName = new File(filePath).getName();
							return new Rom(fileName.substring(0, fileName.length() - 4), filePath);
						}
					}
				})
				.doOnNext(new Consumer<Rom>() {
					@Override
					public void accept(Rom rom) {
						roms.add(rom);
					}
				})
				.doOnComplete(new Action() {
					@Override
					public void run() {
						List<String> romPaths = new ArrayList<>();
						for (Rom rom : roms)
							romPaths.add(rom.getPath());

						romLocationCache.cacheRomPaths(romPaths)
								.subscribe();
					}
				});
	}

	@Override
	protected void onCleared() {
		super.onCleared();
	}
}
