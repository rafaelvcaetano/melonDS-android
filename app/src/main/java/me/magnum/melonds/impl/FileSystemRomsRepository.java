package me.magnum.melonds.impl;

import android.content.Context;
import android.os.Environment;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import io.reactivex.*;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import me.magnum.melonds.model.Rom;
import me.magnum.melonds.model.RomConfig;
import me.magnum.melonds.repositories.RomsRepository;
import me.magnum.melonds.utils.RomProcessor;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class FileSystemRomsRepository implements RomsRepository {
    private static final String ROM_CACHE_FILE = "rom_cache.json";

    private Context context;
    private JsonAdapter<List<Rom>> romListJsonAdapter;

    private boolean areRomsLoaded = false;
    private ArrayList<Rom> roms;

    public FileSystemRomsRepository(Context context, Moshi moshi) {
        this.context = context;

        Type romListType = Types.newParameterizedType(List.class, Rom.class);
        this.romListJsonAdapter = moshi.adapter(romListType);
    }

    @Override
    public Observable<Rom> getRoms(boolean force) {
        if (areRomsLoaded && !force) {
            return Observable.fromIterable(roms);
        } else {
            return loadCachedRoms(force);
        }
    }

    @Override
    public void updateRomConfig(Rom rom, RomConfig romConfig) {
        int romIndex = roms.indexOf(rom);
        if (romIndex < 0)
            return;

        rom.setConfig(romConfig);
        roms.set(romIndex, rom);
        cacheRoms(roms).subscribe();
    }

    private Observable<Rom> loadCachedRoms(boolean force) {
        Single<List<Rom>> initialListObservable;
        if (force) {
            initialListObservable = Observable.fromArray()
                    .cast(Rom.class)
                    .toList();
        } else {
            initialListObservable = this.getCachedRoms()
                    .filter(new Predicate<Rom>() {
                        @Override
                        public boolean test(Rom rom) {
                            return new File(rom.getPath()).isFile();
                        }
                    })
                    .toList();
        }

        final ArrayList<Rom> loadedRoms = new ArrayList<>();
        return initialListObservable
                .flatMapObservable(new Function<List<Rom>, Observable<Rom>>() {
                    @Override
                    public Observable<Rom> apply(final List<Rom> cachedRoms) {
                        return Observable.create(new ObservableOnSubscribe<Rom>() {
                            private void findFiles(File directory, ObservableEmitter<Rom> emitter) {
                                File[] files = directory.listFiles();
                                if (files == null)
                                    return;

                                for (File f : files) {
                                    if (f.isDirectory())
                                        findFiles(f, emitter);

                                    if (romListContainsFile(cachedRoms, f.getAbsolutePath()))
                                        continue;

                                    String fileName = f.getName();

                                    // TODO: support zip files
                                    if (!fileName.endsWith(".nds"))
                                        continue;

                                    String filePath = f.getAbsolutePath();
                                    try {
                                        String romName = RomProcessor.getRomName(new File(filePath));
                                        emitter.onNext(new Rom(romName, filePath, new RomConfig()));
                                    } catch (Exception e) {
                                        e.printStackTrace();

                                        String name = new File(filePath).getName();
                                        emitter.onNext(new Rom(name.substring(0, name.length() - 4), filePath, new RomConfig()));
                                    }
                                }
                            }

                            @Override
                            public void subscribe(ObservableEmitter<Rom> emitter) {
                                findFiles(Environment.getExternalStorageDirectory(), emitter);
                                emitter.onComplete();
                            }
                        }).startWith(cachedRoms);
                    }
                })
                .doOnNext(new Consumer<Rom>() {
                    @Override
                    public void accept(Rom rom) {
                        loadedRoms.add(rom);
                    }
                })
                .doOnComplete(new Action() {
                    @Override
                    public void run() {
                        if (areRomsLoaded) {
                            roms.clear();
                        } else {
                            roms = new ArrayList<>();
                        }

                        roms.addAll(loadedRoms);
                        areRomsLoaded = true;
                        cacheRoms(loadedRoms).subscribe();
                    }
                });
    }

    private Observable<Rom> getCachedRoms() {
        return Observable.create(new ObservableOnSubscribe<Rom>() {
            @Override
            public void subscribe(ObservableEmitter<Rom> emitter) throws Exception {
                File cacheFile = new File(context.getCacheDir(), ROM_CACHE_FILE);
                if (!cacheFile.isFile()) {
                    emitter.onComplete();
                    return;
                }

                BufferedSource source = Okio.buffer(Okio.source(cacheFile));
                List<Rom> roms = romListJsonAdapter.fromJson(source);
                if (roms != null) {
                    for (Rom rom : roms) {
                        emitter.onNext(rom);
                    }
                }

                emitter.onComplete();
                source.close();
            }
        });
    }

    private Completable cacheRoms(final List<Rom> roms) {
        return Completable.create(new CompletableOnSubscribe() {
            @Override
            public void subscribe(CompletableEmitter emitter) throws Exception {
                File cacheFile = new File(context.getCacheDir(), ROM_CACHE_FILE);
                BufferedSink sink = Okio.buffer(Okio.sink(cacheFile));
                romListJsonAdapter.toJson(sink, roms);
                sink.close();

                emitter.onComplete();
            }
        });
    }

    private boolean romListContainsFile(List<Rom> roms, String filePath) {
        for (Rom rom : roms) {
            if (rom.getPath().equals(filePath))
                return true;
        }
        return false;
    }
}
