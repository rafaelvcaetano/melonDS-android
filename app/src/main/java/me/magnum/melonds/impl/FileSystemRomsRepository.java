package me.magnum.melonds.impl;

import android.content.Context;
import android.util.Log;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import io.reactivex.*;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;
import me.magnum.melonds.model.Rom;
import me.magnum.melonds.model.RomConfig;
import me.magnum.melonds.model.RomScanningStatus;
import me.magnum.melonds.repositories.RomsRepository;
import me.magnum.melonds.repositories.SettingsRepository;
import me.magnum.melonds.utils.RomProcessor;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class FileSystemRomsRepository implements RomsRepository {
    private static final String TAG = "FSRomsRepository";
    private static final String ROM_DATA_FILE = "rom_data.json";

    private Context context;
    private SettingsRepository settingsRepository;
    private JsonAdapter<List<Rom>> romListJsonAdapter;
    private BehaviorSubject<List<Rom>> romsSubject;
    private BehaviorSubject<RomScanningStatus> scanningStatusSubject;

    private boolean areRomsLoaded = false;
    private ArrayList<Rom> roms;

    public FileSystemRomsRepository(Context context, Moshi moshi, SettingsRepository settingsRepository) {
        this.context = context;
        this.settingsRepository = settingsRepository;

        Type romListType = Types.newParameterizedType(List.class, Rom.class);
        this.romListJsonAdapter = moshi.adapter(romListType);
        this.romsSubject = BehaviorSubject.create();
        this.scanningStatusSubject = BehaviorSubject.createDefault(RomScanningStatus.NOT_SCANNING);
        this.roms = new ArrayList<>();

        this.romsSubject
                .subscribeOn(Schedulers.io())
                .subscribe(new Consumer<List<Rom>>() {
                    @Override
                    public void accept(List<Rom> roms) {
                        saveRomData(roms);
                    }
                });

        settingsRepository.observeRomSearchDirectories()
                .subscribe(new Consumer<String[]>() {
                    @Override
                    public void accept(String[] directories) {
                        onRomSearchDirectoriesChanged(directories);
                    }
                });
    }

    private void onRomSearchDirectoriesChanged(String[] searchDirectories) {
        ArrayList<Rom> romsToRemove = new ArrayList<>();

        for (Rom rom : roms) {
            File romFile = new File(rom.getPath());
            if (!romFile.isFile()) {
                romsToRemove.add(rom);
                continue;
            }

            boolean isInDirectories = false;
            for (String directory : searchDirectories) {
                File dir = new File(directory);
                if (!dir.isDirectory())
                    continue;

                if (romFile.getAbsolutePath().startsWith(dir.getAbsolutePath())) {
                    isInDirectories = true;
                    break;
                }
            }

            if (!isInDirectories)
                romsToRemove.add(rom);
        }

        for (Rom rom : romsToRemove) {
            removeRom(rom);
        }

        rescanRoms();
    }

    @Override
    public Observable<List<Rom>> getRoms() {
        if (!areRomsLoaded) {
            areRomsLoaded = true;
            loadCachedRoms();
        }

        return this.romsSubject;
    }

    @Override
    public Observable<RomScanningStatus> getRomScanningStatus() {
        return this.scanningStatusSubject;
    }

    @Override
    public void updateRomConfig(Rom rom, RomConfig romConfig) {
        int romIndex = roms.indexOf(rom);
        if (romIndex < 0)
            return;

        rom.setConfig(romConfig);
        roms.set(romIndex, rom);
        onRomsChanged();
    }

    @Override
    public void rescanRoms() {
        this.scanForNewRoms()
                .subscribeOn(Schedulers.io())
                .subscribe(new Observer<Rom>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        scanningStatusSubject.onNext(RomScanningStatus.SCANNING);
                    }

                    @Override
                    public void onNext(Rom rom) {
                        addRom(rom);
                    }

                    @Override
                    public void onError(Throwable e) {
                    }

                    @Override
                    public void onComplete() {
                        scanningStatusSubject.onNext(RomScanningStatus.NOT_SCANNING);
                    }
                });
    }

    private void addRom(Rom rom) {
        if (this.roms.contains(rom))
            return;

        this.roms.add(rom);
        onRomsChanged();
    }

    private void removeRom(Rom rom) {
        if (this.roms.remove(rom))
            onRomsChanged();
    }

    private void onRomsChanged() {
        romsSubject.onNext(new ArrayList<>(roms));
    }

    private void loadCachedRoms() {
        this.getCachedRoms()
                .filter(new Predicate<Rom>() {
                    @Override
                    public boolean test(Rom rom) {
                        return new File(rom.getPath()).isFile();
                    }
                })
                .toList()
                .doOnSuccess(new Consumer<List<Rom>>() {
                    @Override
                    public void accept(List<Rom> cachedRoms) throws Exception {
                        roms.addAll(cachedRoms);
                        onRomsChanged();
                    }
                })
                .flatMapObservable(new Function<List<Rom>, ObservableSource<Rom>>() {
                    @Override
                    public ObservableSource<Rom> apply(List<Rom> roms) {
                        return scanForNewRoms();
                    }
                })
                .subscribeOn(Schedulers.io())
                .subscribe(new Observer<Rom>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        scanningStatusSubject.onNext(RomScanningStatus.SCANNING);
                    }

                    @Override
                    public void onNext(Rom rom) {
                        addRom(rom);
                    }

                    @Override
                    public void onError(Throwable e) {
                    }

                    @Override
                    public void onComplete() {
                        scanningStatusSubject.onNext(RomScanningStatus.NOT_SCANNING);
                    }
                });
    }

    private Observable<Rom> scanForNewRoms() {
        return Observable.create(new ObservableOnSubscribe<Rom>() {
            private void findFiles(File directory, ObservableEmitter<Rom> emitter) {
                File[] files = directory.listFiles();
                if (files == null)
                    return;

                for (File f : files) {
                    if (f.isDirectory())
                        findFiles(f, emitter);

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
                for (String directory : settingsRepository.getRomSearchDirectories())
                    findFiles(new File(directory), emitter);
                emitter.onComplete();
            }
        });
    }

    private Observable<Rom> getCachedRoms() {
        return Observable.create(new ObservableOnSubscribe<Rom>() {
            @Override
            public void subscribe(ObservableEmitter<Rom> emitter) throws Exception {
                File cacheFile = new File(context.getFilesDir(), ROM_DATA_FILE);
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

    private void saveRomData(List<Rom> romData) {
        File cacheFile = new File(context.getFilesDir(), ROM_DATA_FILE);
        BufferedSink sink = null;

        try {
            sink = Okio.buffer(Okio.sink(cacheFile));
            romListJsonAdapter.toJson(sink, romData);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save ROM data", e);
        } finally {
            if (sink != null) {
                try {
                    sink.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
