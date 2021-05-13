package me.magnum.melonds.impl

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.reactivex.*
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import me.magnum.melonds.common.romprocessors.RomFileProcessorFactory
import me.magnum.melonds.domain.model.Rom
import me.magnum.melonds.domain.model.RomConfig
import me.magnum.melonds.domain.model.RomScanningStatus
import me.magnum.melonds.domain.repositories.RomsRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.extensions.addTo
import me.magnum.melonds.utils.FileUtils
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.io.OutputStreamWriter
import java.lang.reflect.Type
import java.util.*
import kotlin.collections.ArrayList

class FileSystemRomsRepository(
        private val context: Context,
        private val gson: Gson,
        private val settingsRepository: SettingsRepository,
        private val romFileProcessorFactory: RomFileProcessorFactory
) : RomsRepository {

    companion object {
        private const val TAG = "FSRomsRepository"
        private const val ROM_DATA_FILE = "rom_data.json"
    }

    private val disposables = CompositeDisposable()
    private val romListType: Type = object : TypeToken<List<Rom>>(){}.type
    private val romsSubject: BehaviorSubject<List<Rom>> = BehaviorSubject.create()
    private val scanningStatusSubject: BehaviorSubject<RomScanningStatus> = BehaviorSubject.createDefault(RomScanningStatus.NOT_SCANNING)
    private val roms: ArrayList<Rom> = ArrayList()
    private var areRomsLoaded = false

    init {
        romsSubject.subscribeOn(Schedulers.io())
                .subscribe { roms -> saveRomData(roms) }
                .addTo(disposables)
        settingsRepository.observeRomSearchDirectories()
                .subscribe { directories -> onRomSearchDirectoriesChanged(directories) }
                .addTo(disposables)
    }

    private fun onRomSearchDirectoriesChanged(searchDirectories: Array<Uri>) {
        // If ROMs have not been loaded yet, there's no point in searching or discarding ROMs now.
        // They will be scanned once needed
        if (!areRomsLoaded)
            return

        // TODO: Check if existing ROMs are still found in the new directory(s). How can we do that reliably using URIs?
        removeAllRoms()
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
                    it.find { rom ->
                        val romPath = FileUtils.getAbsolutePathFromSAFUri(context, rom.uri)
                        romPath == path
                    }?.let { rom -> Maybe.just(rom) } ?: Maybe.empty()
                }
    }

    override fun getRomAtUri(uri: Uri): Maybe<Rom> {
        return getRoms().firstElement()
                .flatMap {
                    it.find { rom ->
                        rom.uri == uri
                    }?.let { rom -> Maybe.just(rom) } ?: Maybe.empty()
                }
    }

    override fun updateRomConfig(rom: Rom, romConfig: RomConfig) {
        val romIndex = roms.indexOf(rom)
        if (romIndex < 0)
            return

        roms[romIndex].config = romConfig
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

    override fun invalidateRoms() {
        if (areRomsLoaded) {
            roms.clear()
            areRomsLoaded = false
        }

        val cacheFile = File(context.filesDir, ROM_DATA_FILE)
        if (cacheFile.isFile) {
            cacheFile.delete()
        }
    }

    private fun addRom(rom: Rom) {
        if (roms.contains(rom))
            return

        roms.add(rom)
        onRomsChanged()
    }

    private fun removeRom(rom: Rom, notifyChanged: Boolean = true) {
        if (roms.remove(rom) && notifyChanged) {
            onRomsChanged()
        }
    }

    private fun removeAllRoms() {
        roms.clear()
        onRomsChanged()
    }

    private fun onRomsChanged() {
        romsSubject.onNext(ArrayList(roms))
    }

    private fun loadCachedRoms() {
        getCachedRoms()
                .filter { rom -> DocumentFile.fromSingleUri(context, rom.uri)?.exists() == true }
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
            private fun findFiles(directory: DocumentFile, emitter: ObservableEmitter<Rom>) {
                val files = directory.listFiles()
                for (file in files) {
                    if (file.isDirectory) {
                        findFiles(file, emitter)
                        continue
                    }

                    romFileProcessorFactory.getFileRomProcessorForDocument(file)?.let { fileRomProcessor ->
                        fileRomProcessor.getRomFromUri(file.uri, directory.uri)?.let { emitter.onNext(it) }
                    }
                }
            }

            override fun subscribe(emitter: ObservableEmitter<Rom>) {
                for (directory in settingsRepository.getRomSearchDirectories()) {
                    val documentFile = DocumentFile.fromTreeUri(context, directory)
                    if (documentFile != null) {
                        findFiles(documentFile, emitter)
                    }
                }

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

            try {
                val roms = gson.fromJson<List<Rom>>(FileReader(cacheFile), romListType)
                if (roms != null) {
                    for (rom in roms) {
                        emitter.onNext(rom)
                    }
                }
                emitter.onComplete()
            } catch (_: Exception) {
                emitter.onComplete()
            }
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