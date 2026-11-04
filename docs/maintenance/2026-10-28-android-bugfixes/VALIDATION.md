# Validazione Android e riproducibilità

Correzioni finali: app `19071123` (core invariato `fd758240`). I successivi commit di questo rapporto non cambiano il codice eseguibile. Una build debug con applicationId `me.magnum.melonds.nightly.dev` tiene separati dati e firma dalle release.

## Esiti finali

| Controllo | Esito | Cosa dimostra |
|---|---|---|
| JVM/Robolectric | **224 test,0 errori/fallimenti/skips** | Regressioni produzione Kotlin, UI, ricerca/selector, storage, import e controlli esistenti. |
| APK ARM64 | Build completata | Compilazione Android con NDK28, non prova hardware. |
| Android Lint | **0 errori,474 warning** | Nessun nuovo errore; i warning non sono nascosti né dichiarati azzerati. |
| Android ART/JNI, API35 ARM64,4KiB | **39 test passati** | Stesse regressioni condivise, MotionEvent, layout, parser/Room e JNI NAND invalidi, più migrazioni. |
| Android ART/JNI, API35 ARM64,16KiB | **39 test passati** | Ripetizione della suite sul secondo ambiente; non equivale a Thor. |
| NDK/UBSan Android4KiB e16KiB | Cinque eseguibili passati su ciascun ambiente | JIT sintetico, callback PCM reale/Oboe, lifecycle audio con adapter, NAND/JNI con modello dichiarato. |
| Native host macOS | Policy RTC/audio, PCM, lifecycle mic, input, file mode, SaveManager, JIT, DNS e NAND passati | Diagnostica del codice condiviso; nessun frontend Mac certificato. |
| CI GitHub | Verificare il run del commit finale su `main`; identificativo nel riepilogo finale e `ci-final.json` privato | Job JVM/lint/APK e sanitizer Linux x86_64/ARM64; i job Android runtime sono locali. |

Il test host `JitDifferentialTest` è stato saltato perché `MELONDS_TEST_ROM` non era impostata durante quella suite; non viene contato tra i test passati. La successiva prova nell’APK con una ROM locale è separata e non sostituisce un differenziale JIT/interprete.

La revisione indipendente ha segnalato tre difetti a `3e0a67af`: cancellazione del parser DAT, EOF/offset accettati come database vuoto, reconnect display insieme a rotazione inset. La sua [valutazione originale](INDEPENDENT-REVIEW.md) è conservata. Tutti e tre sono stati riprodotti nuovamente con test permanenti JVM e Android prima della correzione; il pass conclusivo include un DAT reale sintetico da100 giochi e un errore SQLite durante il suo import. Nessuna seconda revisione è simulata.

## Ambiente conservato sul Mac

- JDK21, SDK Android35, NDK28.0.13004108, CMake3.22.1; emulator37.2.12.
- AVD `melonds-buglab-api35-arm64-4k`, immagine `system-images;android-35;default;arm64-v8a`.
- AVD `melonds-buglab-api35-arm64-16k`, immagine `system-images;android-35;google_apis_ps16k;arm64-v8a`.
- Accelerazione host Apple Silicon, rendering SwiftShader, audio host disabilitato. L’audio numerico è testato; l’ascolto e l’hardware non lo sono.
- Verificare sempre `adb devices`, `adb -s SERIAL shell getprop ro.build.version.sdk`, `getprop ro.product.cpu.abilist` e `getconf PAGESIZE`; non associare un seriale a una Thor per supposizione.

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
sh ./gradlew :app:testGitHubNightlyDebugUnitTest \
  :app:assembleGitHubNightlyDebug :app:lintGitHubNightlyDebug \
  -Pandroid.injected.build.abi=arm64-v8a --max-workers=2 --console=plain
ANDROID_SERIAL=DEVICE_SERIAL sh ./gradlew :app:connectedGitHubNightlyDebugAndroidTest \
  -Pandroid.injected.build.abi=arm64-v8a --max-workers=2 --console=plain
tools/android-tests/build.sh
for regression in JitInvalidationTest MicInputGainTest MicSourceSwitchTest NandJniTest StorageNandTest; do
  tools/android-tests/run.sh --serial DEVICE_SERIAL --test "$regression" || exit
done
bash tools/host-tests.sh
bash tools/host-tests/nand-jni/run.sh
bash tools/host-tests/dns/run.sh
```

I test condivisi sono sotto `app/src/sharedTest/kotlin`, collegati sia a `test` sia ad `androidTest` con il source set Kotlin integrato AGP9. Le classi wrapper applicano Robolectric soltanto sul JVM e AndroidJUnit4 su ART. WorkManager scheduling/notifiche sono adattatori di test: parser, import worker, file I/O e Room/SQLite vengono realmente eseguiti. Il metodo privato di import è invocato per isolare il lavoro dall’interfaccia SAF/foreground service; la prova manuale nell’APK usa invece il picker SAF normale.

## Evidenze RED e GREEN

Directory privata `build/android-bug-audit/`, conservata anche nel backup esterno della campagna. I fixture di regressione e i comandi sono in Git; ROM/save e screenshot del gioco restano fuori Git.

- `audio/`: sei conversioni PCM errate e overflow, copia negativa e vecchio cursore/sessione, controlli su Android/host.
- `storage/`: baseline APK con NAND/BIOS invalidi segnalati come successo; JNI modello, header/TMD, cleanup; cinque casi metadati su JVM/Android.
- `display/`: disconnessione e quattro transizioni multitouch; `review-red.log` combina reconnect/rotazione inset.
- `cheats/`: `streaming-red.log`(4/6 fallivano), `import-expanded-red.log`(5/7 fallivano), `review-red.log` e `review-offset-red.log`; category e integrated GREEN separati.
- `final-review-probes.{py,log}`: diagnosi indipendente del blocco con100 giochi e dell’EOF esatto; processo separato terminato dopo l’osservazione, senza lasciare il parser bloccato nell’app.
- `runtime/final-4k.log`, `runtime/final-16k.log`, XML JUnit/lint, log NDK per ciascun eseguibile/pagina, hash APK e manifest dell’archivio.

Gli errori di preparazione dei test (source set Kotlin, nomi di metodo incompatibili con DEX, include C++) non sono contati come regressioni del prodotto. Una diagnosi ASan/UBSan dinamica aveva segnalato un’incompatibilità di namespace ABI dello stub; il percorso permanente usa il runtime statico NDK, senza soppressioni di bug. Le evidenze sono state riprodotte e verificate nella revisione finale.

## Prova ROM e limiti

Per Sacred Gold Plus sono stati forniti percorsi e hash di fixture locali congelate. Sono state copiate, mai riscritte in origine. Il caso usa SGP1.2.5 IT e il raw avanzato già collaudato nella campagna ROM; nessun savestate Mac viene caricato su Android. Il dettaglio della prova è in [GAME-CHECK.md](GAME-CHECK.md).

Questa è una prova funzionale breve dell’APK su Android virtuale. Restano fuori dalla certificazione: Thor fisica, driver/GPU e microfono reali, giochi e scene di freeze senza raw riproducibile, WFC/peer, RetroAchievements/autenticazione, NAND reale/quota/HOME, profili di memoria estremi. Le [decisioni complete](DECISIONS.md) indicano il costo di questi limiti. Nessuna modifica prestazionale è rivendicata senza misure.

Metodo basato sulle fonti primarie: [fondamenti dei test Android](https://developer.android.com/training/testing/fundamentals), [Android Emulator](https://developer.android.com/studio/run/emulator), [test dei Worker](https://developer.android.com/develop/background-work/background-tasks/testing/persistent/worker-impl), [Kotlin integrato AGP](https://developer.android.com/build/migrate-to-built-in-kotlin), [benchmark Android](https://developer.android.com/topic/performance/benchmarking/benchmarking-overview).
