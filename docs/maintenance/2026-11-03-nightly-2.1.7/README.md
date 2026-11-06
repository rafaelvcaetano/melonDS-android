# melonDS Android 2.1.7 — verifica e consegna locale

> **Superseded for the current candidate:** use the
> [second independent scan and 2.1.8/49 delivery](../2026-11-03-security-followup/README.md).
> This page remains the immutable evidence for the retained 2.1.7/48 fallback.

Revisione del 3 novembre 2026. Le bozze della revisione restano locali; le
panoramiche correnti sono [app](../../../README.md#whats-new) e
[core](https://github.com/Umberto-DEV/melonDS-android-lib#whats-new).
Titoli proposti allora:

- App: **Improve setup, cheats and Android reliability**.
- Core: **Fix core memory handling and NAND saves**.

Questa consegna si ferma a documenti, test Android sul Mac e APK locale. La Thor
non è stata aggiornata. Nessuna nuova PR, issue o release è stata pubblicata;
nessun push o commit è stato eseguito in questa revisione.

## Completezza dell'elenco

Le bozze comprendono l'intero fork rispetto alla Nightly ufficiale, non soltanto
l'ultima scansione. Sono state confrontate con lo storico Git, la
[matrice issue/PR](../2026-10-28-refinement/ISSUE-PR-COVERAGE.csv), il
[resoconto delle integrazioni precedenti](../2026-10-28-refinement/INTEGRATION-REPORT.md),
i report di ottobre/novembre e i file modificati dalla remediation.

| Periodo / origine | App | Core | Prova canonica |
| --- | --- | --- | --- |
| Nightly ufficiale → fork già usato sulla Thor | Setup BIOS, WFC/DNS, backup, ricerca/selettori cheat, controlli; correzioni save, microfono/audio, libreria, import, JNI, display, filtri e lifecycle | SCFG DS/DSi, power saving Wi-Fi, literal/maschera JIT, teardown, DTCM, lavoro renderer e API DNS | [Confronto con la Nightly e provenienza](../2026-10-29-nightly-comparison/README.md), [prima/dopo](../2026-10-29-nightly-comparison/VALIDATION.md) |
| 31 ottobre–1 novembre | Lease JNI degli URI; consegna 2.1.6 | Import NAND, header FAT, sostituzione protetta del save | [Import e RTC](../2026-10-31-rtc-nand/README.md), [persistenza](../2026-10-31-persistence-lifecycle/README.md), [Thor](../2026-11-01-thor-update/README.md) |
| 2–3 novembre, prima della scansione completata | Aggiornamenti del gitlink e harness | Primo fold Thumb/DTCM, rollback dopo rename fallito, lunghezze DS/DSi Wi-Fi, Action Replay, lettura B7 NAND | Le sei unità sono elencate con link separati nella bozza core e nella matrice canonica |
| Scansione completata e revisione successiva | Input/intent, cache, import, stato, ripristino, credenziali/backup, trasporto e CI | Formati/stati nativi, rete, memoria Wi-Fi, firmware, NAND, GBA, DSP e grafica | [35 occurrence](../2026-11-03-security-remediation/FINDINGS.csv), [titoli esatti](../2026-11-03-security-remediation/FINDING-TITLES.md), [correzioni successive](../2026-11-03-security-remediation/ADVERSARIAL-REVIEW.md) |

La riconciliazione usa i CSV/titoli versionati e le bozze della remediation.
La rilettura del job live tramite il servizio Security non ha completato la
richiesta ed è stata interrotta; non è stata avviata né cancellata alcuna scansione.
Questa revisione non attesta nuovamente il modello dei worker Daybreak.
La riconciliazione documentale delle occurrence è esatta:

| Proprietà | Numero |
| --- | ---: |
| App soltanto | 15 |
| Core soltanto | 17 |
| Condivise app/core | 3 |
| Unione | **35** |
| Voci della proposta app / core, includendo le condivise | 18 / 20 |
| ID mancanti o aggiunti rispetto al CSV dei risultati | **0 / 0** |
| Gravità assegnata dalla scansione: alta / media / bassa | **12 / 17 / 6** |

35 è il numero delle segnalazioni salvate, non il numero di cause indipendenti
né il totale di tutti i bug storici. Non sommare 18+20 e non contare di nuovo
le cause Wi-Fi della vecchia scansione cancellata. Le correzioni della revisione
successiva sono riportate separatamente e non ricevono ID inventati.
Tutte le 35 occurrence hanno una correzione nel sorgente. Alcune prove sono
RED/GREEN dinamiche; altre tracciano il chiamante e verificano limiti adiacenti.
Non sono 35 exploit riprodotti su Android. La scansione aveva copertura parziale
dei file: 74 completamente revisionati su 3.588 nell'inventario.

L'elenco risultava già presente ma disperso. Sono stati eliminati dalle bozze
il gitlink indicato ancora come futuro, i paragrafi aggiunti in fondo che
mescolavano build differenti e i link relativi inutilizzabili nella PR core.
Ogni gruppo di fix ora punta a un report con correzione e verifica; gli ID esatti
restano nel CSV canonico. Le bozze storiche ventuno rimandano alle nuove.
La vecchia procedura di settembre è marcata superata, soprattutto per i comandi
di disinstallazione che non vanno usati per aggiornare la Thor attuale.

## Valutazione di criticità e beneficio

La gravità alta/media/bassa sopra appartiene alla scansione. La valutazione
pratica seguente distingue il rischio corretto dal risultato osservato.

| Priorità pratica | Correzioni | Beneficio e limite |
| --- | --- | --- |
| Alta: dati personali | Scritture save/NAND, rollback, transazioni Room, restore controller/layout e recupero AtomicFile | Riduce il rischio di perdere o adottare dati incompleti dopo errori/cancellazioni. Non rende FAT/SAF atomici in caso di perdita di alimentazione o scrittori esterni. |
| Alta: memoria nativa | ROM/savestate/firmware, transfer, Wi-Fi, GBA, DSP/GPU e cheat | Blocca accessi fuori limite o stati incoerenti raggiungibili dai percorsi documentati. Non dimostra assenza di altri difetti né esecuzione di codice su dispositivo. |
| Alta: autorità e segreti | Intent/URI, password/token RA, backup e dipendenze CI | Riduce l'accesso indebito ai file e l'esposizione di credenziali; rende verificabili le dipendenze. L'effetto non si misura in FPS. |
| Media: continuità d'uso | Libreria/icone, cancellazione import, audio/input, JNI e fallback renderer | Evita blocchi, input trattenuti, risorse accumulate e alcune uscite anomale, nei casi coperti. Driver, periferiche e giochi specifici richiedono prove fisiche. |
| Miglioria funzionale | Setup guidato, WFC, cheat search/selector, backup e controlli | Riduce i passaggi manuali e rende disponibili funzioni richieste. I difetti riparati nelle nuove schermate del fork non erano schermate della Nightly originale. |
| Ottimizzazione locale | Compute invariato e pass edge inutilizzato | Evita lavoro ridondante. Non sono dimostrati guadagni di FPS, consumi, RAM, latenza o qualità grafica. |

Due cambiamenti meritano particolare prova di compatibilità: il download
metadata DSiWare è disattivato senza una sorgente HTTPS fidata configurata;
slirp blocca destinazioni private/loopback e il protocollo LAN serializza i
player tramite campi validati. Non presentarli come cambiamenti invisibili.

## Baseline, sorgenti e APK

Il 3 novembre le API GitHub confermano ancora app upstream master `c42995caff0144f37ad9791dc9bc96d82e8a6f65`,
core upstream master `431ab4bd0003c4356e25fce89640ae1004579b9b` e
asset Nightly aggiornato il 12 settembre, digest
`addc921e5ea539841b0de87b8cb7e00515a9fcce32191b5cdf7fdcb08e4f1b44`.
Le vecchie PR app #1681/#1682 e core #18/#19 sono ancora chiuse senza merge.
Non è stato scaricato un altro duplicato della Nightly.

Entrambi i checkout restano su `main`. App HEAD `9929f530df6f60b86d50c324f4aec83747e5745b`,
ultimo commit del codice security `a77ccaaa`; core e gitlink
`65952aec868bedd2dc33151ed850804e3e13df05`, working tree core pulito.
La build include le modifiche locali preesistenti conservate e il nuovo numero
2.1.7/48; HEAD da solo non identifica il binario. Patch, hash dei file iniziali,
hash del delta funzionale e identità APK sono nel dossier locale
`local/notes/pr-review-2026-11-03/`. Non vengono pubblicati dati privati o ROM.

| APK ottimizzato | Valore |
| --- | --- |
| Versione / codice | 2.1.7 GH (NIGHTLY) (PROFILING) / 48 |
| Pacchetto | `me.magnum.melonds.nightly.perf` |
| ABI frontend / API minima | ARM64 / 24 |
| Modalità | RelWithDebInfo, ottimizzato, non debuggable, non testOnly |
| Byte | 85496522 |
| SHA-256 | `bee6fded3ba4ace159c89cccf0eb21b360f4defd66eb5c5b9bc82f73f2e6606f` |
| Certificato SHA-256 | `41ad82cd4365c73195128975cf0b920699f8833093507957dc2d0f9d6bb0b4b1` |
| Copia canonica | `local/notes/pr-review-2026-11-03/candidate/melonDS-2.1.7-nightly-profiling-arm64.apk` |
| Fallback | APK 2.1.6/47 nel [dossier originale](../2026-10-31-nightly-2.1.6/README.md) |

Firma e pacchetto sono compatibili con la Thor documentata, con versionCode
maggiore. Questa compatibilità non sostituisce la rilettura del dispositivo
immediatamente prima di una futura installazione autorizzata.

## Verifiche di questa build

Tutti i risultati sotto sono di questa consegna, non trasferiti dalla 2.1.6.
Il [riepilogo leggibile da script](CHECKS.json) contiene hash e conteggi.

| Livello | Risultato attuale | Limite |
| --- | --- | --- |
| JVM/Robolectric | 311 test app + 1 API RA, zero errori/fallimenti/skip | Non è ART |
| Build Android | APK profiling ARM64, debug e instrumentation completati | Non è esecuzione |
| Lint profiling | 0 errori, 478 warning | Non è una build priva di warning |
| Host macOS | 163 controlli ASan/UBSan; build core sanitizer e suite policy/save/JIT/NAND/RTC; DNS e JNI separati passati | Non è frontend Mac o runtime Android; JIT differenziale con ROM saltato |
| NDK Android | 11 eseguibili con exit zero su API35 ARM64, pagine 4096 e 16384 byte | Eseguibili componenti; i modelli JNI non diventano ART |
| ART `.dev`, API35/4 KiB | 52 passaggi + 3 skip opt-in previsti; WorkManager separato 1 passaggio | Non tutte le nuove verifiche security JVM hanno un equivalente ART |
| GLES reale `.dev` | Software, Compute-fallback e OpenGL-fallback: 3 passaggi, due fasi native ciascuno e preferenze invariate | GLES 3.0 del traduttore AVD, non driver Adreno Thor |
| APK `.perf` esatto | Installato sull'AVD 4 KiB; homebrew avviata dalla libreria con grant SAF, due fasi cromatiche complementari osservate sui due schermi | Sessione sintetica breve, non gioco commerciale, SAVE/DSi o test prolungato |
| Integrità CI | Policy Action/Gradle passata | Nessuna nuova esecuzione CI remota |
| Documenti/sorgenti | Link/anchor locali verificati; diff --check app/core passato; modifiche preesistenti protette ricontrollate | Alcuni URL pubblici richiedono ancora commit/push |

Target eseguiti: JitInvalidationTest, MicInputGainTest, MicSourceSwitchTest,
NandJniTest, StorageNandTest, UtfLeaseTest, RendererFallbackTest, NandFatTest,
ExistingSaveReplacementTest, ReplacementFaultSweepTest e RtcResumeTest.
AVD usati: `melonds-buglab-api35-arm64-4k` e `melonds-buglab-api35-arm64-16k`,
seriali espliciti `emulator-5560` e `emulator-5562`, chiusi dopo le prove.
Il runtime è Android 15 `AE3A.240806.019/12368160`, ARM64.

[Prima fase dell'APK ottimizzato](profiling-phase-1.png) e
[seconda fase](profiling-phase-2.png): i pixel campionati passano da
blu/rosso a rosso/blu. La fixture originale da 131.072 byte ha SHA-256
`e298b528668165047d8bb4d7dfa0df038ddb9a6d91289b6f7a84b55e8b2bd5f4`.
Nell'intervallo limitato di log dell'APK profiling non compaiono FATAL/crash.
Non è una certificazione di assenza di bug.

Note operative conservate nel dossier: un primo lancio da shell con grant URI
non posseduto è stato respinto da Android; la prova valida usa il picker SAF.
Una prima chiamata usava un nome Activity errato e non ha avviato l'app; è stato
poi usato il launcher letto dal manifest. Il recupero di un'impostazione AVD
vuota ha richiesto quoting esplicito; il valore iniziale è stato ripristinato.
L'analisi screenshot ha usato il decoder PNG locale perché Pillow non era
installato. Questi tentativi non sono contati come difetti di melonDS o test
passati. Font, dimensioni/densità, rotazione e conferma fullscreen temporanei
sono stati ripristinati. L’AVD 4 KiB non terminava dopo `emu kill`: è stato
arrestato soltanto il suo PID identificato, con verifica finale di assenza del
processo; nessun arresto globale di emulatori o processi SGP. Nessun dato personale è stato cancellato.

## Ripetere i controlli Android sul Mac

Usare JDK 21, NDK 28.0.13004108 e gli AVD ARM64 esistenti. Non compilare il
frontend desktop Mac per validare questa app. Prima controllare lo spazio:
riserva minima 10 GiB, con budget di picco per build/AVD.

```sh
python3 tools/android-tests/renderer-fixture/generate.py
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home sh ./gradlew :app:testGitHubNightlyDebugUnitTest :rcheevos-api:test :app:assembleGitHubNightlyProfiling :app:lintGitHubNightlyProfiling :app:assembleGitHubNightlyDebug :app:assembleGitHubNightlyDebugAndroidTest --max-workers=2 --console=plain -Pandroid.injected.build.abi=arm64-v8a -Pandroid.injected.testOnly=false
bash tools/host-tests/security/run.sh
bash tools/host-tests.sh
bash tools/host-tests/dns/run.sh
bash tools/host-tests/nand-jni/run.sh
python3 .github/check_ci_integrity.py
BUILD_JOBS=2 bash tools/android-tests/build.sh --abi arm64-v8a
```

Gli APK di questa versione Gradle sono sotto `app/build/intermediates/apk/`.
Verificare il percorso stampato dalla build; non assumere quello storico sotto
`outputs/apk/`. Installare app `.dev` e test APK con `adb -s SERIAL install -r -t --no-incremental`.
Eseguire la suite ART e WorkManager separato come nella
[guida di verifica](../2026-10-28-refinement/VALIDATION.md#android-art-and-provider-behavior).
Eseguire gli 11 target elencati in `tools/android-tests/build.sh` con `run.sh --serial SERIAL --test NOME`.
Per la homebrew, Software/Compute/OpenGL e gli oracoli dei fotogrammi seguire la
[guida GLES](../../../tools/android-tests/renderer-fixture/README.md).
Uno skip o il solo exit zero di adb non sono un test passato.

## Passaggio successivo sulla Thor

1. Attendere l'autorizzazione specifica alla prova/installazione. Questa consegna
   non la esegue. Usare sempre un seriale esplicito, mai il default ADB.
2. Rileggere versione, pacchetto e firma installati; se differiscono dalle
   identità documentate, fermarsi e conservare i dati.
3. Verificare backup leggibili di save, NAND/firmware, database/cheat, impostazioni,
   permessi URI e APK attuale. Preservare gli originali; giocare su copie.
4. Verificare SHA-256 e certificato del candidato. Per l'aggiornamento compatibile
   usare soltanto `adb -s SERIAL install -r --no-incremental APK`.
   Non disinstallare, non usare clear-data e non cambiare firma.
5. Rileggere UID, grants, impostazioni e hash protetti, poi provare libreria,
   Continue/SAVE/cold Continue su una copia, pausa/ripresa, audio, controlli,
   doppio display e renderer usato sulla Thor. Annotare le opzioni esatte.
6. Separare la prova DSi/SAF/NAND e quella Wi-Fi/RA/LAN; nessuna accettazione
   generale di queste aree segue dalla sola homebrew.

## Documenti e pubblicazione

I link delle bozze sono URL assoluti del fork app, utilizzabili anche nel corpo
della PR core. I target e gli anchor sono verificati contro i file locali.
Alcuni report storici erano già untracked e questo report è nuovo: i relativi
URL GitHub richiedono un successivo commit/push del materiale revisionato.
Non dichiarare tutti i link pubblici già disponibili. Prima della PR servono
link pubblici funzionanti, CI della revisione esatta, link reciproci reali e
l'esplicita autorizzazione che rimuove il blocco di pubblicazione.

Restano separati: Thor, giochi commerciali, DSi HOME/quota, ARMv7/x86_64 Android,
Android 9, hardware microfono/Bluetooth/controller/display e prestazioni.
Il test JIT differenziale con ROM esterna è saltato; il JIT sintetico è eseguito.

## Spazio e conservazione

Spazio iniziale circa 16 GiB. Budget aggiuntivo dichiarato 5 GiB; durante le build
è rimasta la riserva minima di 10 GiB. Analisi sola lettura: circa 18 GiB ciascuno
per Archivio-ROM-Thor e Sacred Gold Plus, 11 GiB di AVD, 4,9 GiB per questo
checkout e 434 MiB di backup melonDS. Il nome di una cartella non autorizza
la cancellazione. La chat SGP era attiva: nessun suo file è stato eliminato.
Backup originali, BIOS, save, fixture, storia Git e prove uniche sono preservati.
La pulizia finale dei soli output riproducibili è registrata in un manifesto
per file nel dossier locale, dopo l'arresto dei produttori.

Pulizia finale: **1.027 file**, **1.613.991.936 byte allocati** (circa 1,50 GiB),
con hash prima della rimozione. Rimangono circa **12,2 GiB liberi**. Eliminati
intermedi `.o/.a`, copie native identiche agli APK, fixture rigenerabili e il
vecchio candidato generato 2.1.5; conservati 2.1.7 e fallback 2.1.6, APK debug/test,
report, script, originali e backup. Nessun file SGP, archivio ROM o AVD condiviso
è stato eliminato.
