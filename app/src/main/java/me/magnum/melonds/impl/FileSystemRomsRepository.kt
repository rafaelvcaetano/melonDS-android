package me.magnum.melonds.impl

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.reactivex.*
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import me.magnum.melonds.model.Rom
import me.magnum.melonds.model.RomConfig
import me.magnum.melonds.model.RomScanningStatus
import me.magnum.melonds.repositories.RomsRepository
import me.magnum.melonds.repositories.SettingsRepository
import me.magnum.melonds.utils.RomProcessor
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.io.OutputStreamWriter
import java.lang.reflect.Type
import java.util.*
import kotlin.collections.ArrayList

class FileSystemRomsRepository(private val context: Context, private val gson: Gson, private val settingsRepository: SettingsRepository) : RomsRepository {
    companion object {
        private const val TAG = "FSRomsRepository"
        private const val ROM_DATA_FILE = "rom_data.json"
    }

    private val romListType: Type = object : TypeToken<List<Rom>>(){}.type
    private val romsSubject: BehaviorSubject<List<Rom>> = BehaviorSubject.create()
    private val scanningStatusSubject: BehaviorSubject<RomScanningStatus> = BehaviorSubject.createDefault(RomScanningStatus.NOT_SCANNING)
    private val roms: ArrayList<Rom> = ArrayList()
    private var areRomsLoaded = false

    init {
        romsSubject
                .subscribeOn(Schedulers.io())
                .subscribe { roms -> saveRomData(roms) }
        settingsRepository.observeRomSearchDirectories()
                .subscribe { directories -> onRomSearchDirectoriesChanged(directories) }
    }

    private fun onRomSearchDirectoriesChanged(searchDirectories: Array<String>) {
        val romsToRemove = ArrayList<Rom>()
        for (rom in roms) {
            val romFile = File(rom.path)
            if (!romFile.isFile) {
                romsToRemove.add(rom)
                continue
            }
            var isInDirectories = false
            for (directory in searchDirectories) {
                val dir = File(directory)
                if (!dir.isDirectory) continue
                if (romFile.absolutePath.startsWith(dir.absolutePath)) {
                    isInDirectories = true
                    break
                }
            }
            if (!isInDirectories) romsToRemove.add(rom)
        }

        for (rom in romsToRemove) {
            removeRom(rom)
        }

        rescanRoms()
    }

    override fun getRoms(): Observable<List<Rom>> {
        if (!areRomsLoaded) {
            areRomsLoaded = true
            loadCachedRoms()
        }
        return romsSubject
    }

    override fun getRomScanningStatus(): Observable<RomScanningStatus> {
        return scanningStatusSubject
    }

    override fun getRomAtPath(path: String): Maybe<Rom> {
        return getRoms().firstElement()
                .flatMap {
                    it.find { rom -> rom.path == path }?.let { rom -> Maybe.just(rom) } ?: Maybe.empty()
                }
    }

    override fun updateRomConfig(rom: Rom, romConfig: RomConfig) {
        val romIndex = roms.indexOf(rom)
        if (romIndex < 0)
            return

        rom.config = romConfig
        roms[romIndex] = rom
        onRomsChanged()
    }

    override fun setRomLastPlayed(rom: Rom, lastPlayed: Date) {
        val romIndex = roms.indexOf(rom)
        if (romIndex < 0)
            return

        rom.lastPlayed = lastPlayed
        roms[romIndex] = rom
        onRomsChanged()
    }

    override fun rescanRoms() {
        scanForNewRoms()
                .subscribeOn(Schedulers.io())
                .subscribe(object : Observer<Rom> {
                    override fun onSubscribe(d: Disposable) {
                        scanningStatusSubject.onNext(RomScanningStatus.SCANNING)
                    }

                    override fun onNext(rom: Rom) {
                        addRom(rom)
                    }

                    override fun onError(e: Throwable) {}
                    override fun onComplete() {
                        scanningStatusSubject.onNext(RomScanningStatus.NOT_SCANNING)
                    }
                })
    }

    private fun addRom(rom: Rom) {
        if (roms.contains(rom)) return
        roms.add(rom)
        onRomsChanged()
    }

    private fun removeRom(rom: Rom) {
        if (roms.remove(rom)) onRomsChanged()
    }

    private fun onRomsChanged() {
        romsSubject.onNext(ArrayList(roms))
    }

    private fun loadCachedRoms() {
        getCachedRoms()
                .filter { rom -> File(rom.path).isFile }
                .toList()
                .doOnSuccess { cachedRoms ->
                    roms.addAll(cachedRoms!!)
                    onRomsChanged()
                }
                .flatMapObservable { scanForNewRoms() }
                .subscribeOn(Schedulers.io())
                .subscribe(object : Observer<Rom> {
                    override fun onSubscribe(d: Disposable) {
                        scanningStatusSubject.onNext(RomScanningStatus.SCANNING)
                    }

                    override fun onNext(rom: Rom) {
                        addRom(rom)
                    }

                    override fun onError(e: Throwable) {}
                    override fun onComplete() {
                        scanningStatusSubject.onNext(RomScanningStatus.NOT_SCANNING)
                    }
                })
    }

    private fun scanForNewRoms(): Observable<Rom> {
        return Observable.create(object : ObservableOnSubscribe<Rom> {
            private fun findFiles(directory: File, emitter: ObservableEmitter<Rom>) {
                val files = directory.listFiles() ?: return
                for (file in files) {
                    if (file.isDirectory) findFiles(file, emitter)
                    val fileName = file.name

                    // TODO: support zip files
                    if (!fileName.endsWith(".nds"))
                        continue

                    val filePath = file.absolutePath
                    try {
                        val romName = RomProcessor.getRomName(File(filePath))
                        emitter.onNext(Rom(romName, filePath, RomConfig()))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        val name = File(filePath).name
                        emitter.onNext(Rom(name.substring(0, name.length - 4), filePath, RomConfig()))
                    }
                }
            }

            override fun subscribe(emitter: ObservableEmitter<Rom>) {
                for (directory in settingsRepository.getRomSearchDirectories())
                    findFiles(File(directory), emitter)

                emitter.onComplete()
            }
        })
    }

    private fun getCachedRoms(): Observable<Rom> {
        return Observable.create(ObservableOnSubscribe { emitter ->
            val cacheFile = File(context.filesDir, ROM_DATA_FILE)
            if (!cacheFile.isFile) {
                emitter.onComplete()
                return@ObservableOnSubscribe
            }

            val roms = gson.fromJson<List<Rom>>(FileReader(cacheFile), romListType)
            if (roms != null) {
                for (rom in roms) {
                    emitter.onNext(rom)
                }
            }
            emitter.onComplete()
        })
    }

    private fun saveRomData(romData: List<Rom>) {
        val cacheFile = File(context.filesDir, ROM_DATA_FILE)

        try {
            val romsJson = gson.toJson(romData)

            val output = OutputStreamWriter(FileOutputStream(cacheFile))
            output.write(romsJson)
            output.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save ROM data", e)
        }
    }
}