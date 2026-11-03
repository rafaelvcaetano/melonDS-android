# Seconda campagna indipendente di sicurezza — 3 novembre 2026

## Esito

La seconda Deep Security Scan è completata e sigillata. Le 66 occurrence nuove
non includono le 35 della campagna precedente. La riconciliazione corrente è:

| Disposizione | Occurrence | Gruppi causali normalizzati |
| --- | ---: | ---: |
| Corrette nel codice del fork su `main` | 62 | 45 |
| Correzione repository presente; controllo di deployment esterno non verificabile | 2 | 1 |
| Non applicabile al target Android | 1 | 1 |
| Informativa, già appartenente alla campagna precedente | 1 | 1 |
| **Totale nuova scansione** | **66** | **48** |

I 48 gruppi comprendono 46 cause correggibili nuove per il registro, una
precondizione non applicabile ad Android e un record informativo preesistente.
Non sono 48 exploit Android. La normalizzazione unisce le
manifestazioni duplicate della stessa causa: tredici record di ripristino
savestate nello stesso gruppo di invarianti, due budget dello stesso import
cheat, due manifestazioni B7/R4, tre lease/limiti RetroAchievements, due gate di
release e due controlli della stessa catena di dipendenze. Tutte le altre
occurrence restano una causa separata. Gli ID, i titoli e la disposizione riga
per riga sono nel registro canonico
[FINDINGS.csv](../2026-11-03-security-remediation/FINDINGS.csv) e nel relativo
[elenco titoli](../2026-11-03-security-remediation/FINDING-TITLES.md). La
[vista Markdown delle 101 occurrence](FINDING-DETAILS.md) collega ogni ID a
riepilogo, percorso, prova, controprova, limitazioni, rimedio, patch e
prima/dopo, mantenendo il CSV come registro canonico.

### Risposta sulla 2.1.7 e provenienza

La prima campagna aveva corretto le **35 occurrence precise** nel codice che
costituiva il candidato 2.1.7/48. La seconda scansione ha usato la stessa coppia
di codice app/core documentata per quel candidato: app `9929f530` (il commit
`a77ccaaa` contiene il codice security precedente) e core `65952aec`.
Pertanto le nuove segnalazioni riguardano rischi già presenti nel candidato
2.1.7, quando il rispettivo percorso è compilato nella variante Nightly. La
prima chiusura non dimostrava la chiusura di tutte le rappresentazioni e di
tutti i consumatori della stessa classe di bug.

| Provenienza causale della seconda scansione | Occurrence | Gruppi |
| --- | ---: | ---: |
| Correzione precedente incompleta o percorso adiacente omesso | 31 | 16 |
| Causa indipendente già presente ma non registrata dalla prima scansione | 33 | 30 |
| Non applicabile ad Android | 1 | 1 |
| Riconciliazione informativa della prima campagna | 1 | 1 |
| **Totale** | **66** | **48** |

Nessuno dei 35 ID precedenti ricompare come occurrence ancora aperta. Questo
non equivale a dire che la protezione fosse completa: tra i 16 gruppi di
correzione incompleta ci sono l'override R4 che aggirava il limite B7, i chunk
interni del deserializzatore RCHV pur usando l'API sized, gli offset metadata
degli archivi, il rollback savestate privo del passaggio in lettura e ulteriori
invarianti native. Le 30 cause indipendenti erano in gran parte in percorsi
upstream già presenti. Due difetti del gruppo incompleto derivavano da funzioni
aggiunte prima nel fork: testo firmware nel backup/restore e limite della copia
NAND del setup guidato. Non è stata dimostrata una nuova primitive creata dai
fix `a77ccaaa` o `65952aec`; le omissioni di copertura non vanno confuse con
una prova di assenza di regressioni future.

Per il Nightly 2.1.7, esclusi informativo, R4 non applicabile, due finding di
build/CI, due di workflow release e il controllo update `gitHubProd` non
compilato in Nightly, **59 occurrence / 43 gruppi** erano associabili al
runtime potenzialmente presente nell'APK. È una relazione sorgente/build, non
una riproduzione dinamica di ciascun caso nel binario.

Nella prima pagina interrogata di entrambe le scansioni, i risultati erano
ancora marcati **Open** (`triage.open`, `remediation.idle`). Lo stato remoto
di tutte le righe non è verificabile. L'unico controllo di remediation esposto
richiede `requestId`, `actionToken` ed `expectedVersion` ottenuti dal flusso UI;
anche lo stadio `verified` non chiude il triage. L'interfaccia Mac era bloccata
alla schermata di login e l'utente ha chiesto di proseguire senza interfaccia
per ora. Nessuna riga è stata marcata Closed per supposizione: le disposizioni
qui sotto sono tecniche e locali, distinte dallo stato Security e da un'eventuale
pubblicazione upstream. I commit di codice sono stati inviati sui `main` dei fork.

## Identità iniziale e conservazione

Prima delle modifiche sono stati registrati branch, HEAD, gitlink, patch staged
e unstaged, file untracked, identità dei candidati, processi e spazio. Le copie
sono nel dossier locale `local/notes/pr-review-2026-11-03/` e nel manifest della
scansione. Il punto di partenza era:

| Elemento | Identità osservata |
| --- | --- |
| App | solo branch `main`; HEAD `9929f530df6f60b86d50c324f4aec83747e5745b`; codice security precedente `a77ccaaa`; working tree già modificato e con file untracked |
| Core/gitlink | solo branch `main`; `65952aec868bedd2dc33151ed850804e3e13df05`; working tree inizialmente pulito |
| Candidato esistente | 2.1.7/48, SHA-256 `bee6fded3ba4ace159c89cccf0eb21b360f4defd66eb5c5b9bc82f73f2e6606f` |
| Spazio | 18.041.880.576 byte liberi; picco aggiuntivo previsto 5 GiB; riserva minima 10 GiB |
| Produttori | nessun writer concorrente app/core; Gradle, ADB e gli AVD avviati in seguito appartenevano a questa campagna |
| Sacred Gold Plus | checkout e output fuori ambito, non letti per la remediation e non modificati o ripuliti |

L'HEAD iniziale non identificava il binario: il candidato finale incorpora il
codice di produzione del commit app `0018cddb` e il core `ac5e6ba0`.
`starting-app.patch`, `starting-core.patch` e
`starting-local-files.json` consentono di distinguere lo stato preesistente.
Non sono stati usati branch aggiuntivi, worktree, clone, reset, stash o rebase.

## Scansione e configurazione osservabile

| Campo | Valore |
| --- | --- |
| Scan ID | `ab26b107-97dd-4557-9c4b-2942e5df879d` |
| Avvio | `2026-11-03T13:56:06.766246Z` |
| Completamento/sigillo | `2026-11-03T17:44:41.144905Z` |
| Stato | `completed`; un avvio, stessa chiamata/cella, una finalizzazione, nessun rilancio |
| Programma/accesso | Daybreak `Daybreak Blue`, accesso concesso; controllo `2026-11-03T13:55:57.305Z`, non stale |
| Chat | reasoning richiesto `xhigh` |
| Modello/reasoning dei worker | **non verificabile**: non esposto dall'interfaccia Security |
| Modalità | `deep_repository`; include `.`; nessuna esclusione o area differita |
| Revisione root | app `9929f530df6f60b86d50c324f4aec83747e5745b` |
| Revisione annidata | core `65952aec868bedd2dc33151ed850804e3e13df05` |
| Snapshot | `codex-security-snapshot/v1:sha256:9ff048237b8d1dae0db1f928f1ad74e89e50d1c13f23bd135df8aa77722c4c22` |
| Target ID | `target_sha256_dfd59708cd87aac8cb3977fa42ae8e93a01b153a892ffa91c1795a3ade93a6e3` |
| Copertura coordinatore | `complete`: 3.633 file non ignorati, inclusi 750 del core |
| Gravità | 17 high, 33 medium, 15 low, 1 informational |
| Prima posizione | 38 app/shared, 28 core; è una classificazione per posizione primaria, non ownership PR |

La configurazione della chat, il diritto di accesso Daybreak e la configurazione
dei worker sono tre elementi distinti. Nessun valore non esposto è stato
inferito. Durante lo snapshot i sorgenti non sono stati modificati. La fase di
remediation è iniziata soltanto dopo il completamento e il sigillo della stessa
scansione. Gli artefatti del coordinatore non sono stati ricreati a mano:

- `findings.json`: SHA-256
  `3e009c6f8476a350b13086ccd21f6c0905dad980432b0a69f6a4b67f29e22430`;
- `coverage.json`: SHA-256
  `5d9167a65165b3e1a12a9f3f983decf2287042a3f2e932e536191ca2a9748a3c`.

## Ricerca, smentite e correzioni

Per ogni occurrence sono stati verificati chiamante reale, input controllabile,
invarianti, consumatori e controlli che potessero smentire l'ipotesi. Una riga
sospetta non è stata trattata come prova. La scansione contiene anche un record
informativo che riconcilia la precedente campagna: non è contato come nuovo bug.

Le correzioni principali sono:

| Layer | Comportamento precedente | Comportamento corretto e beneficio |
| --- | --- | --- |
| Core, periferiche e cartucce | cursori BPTWL, risposte SD, trasferimenti NAND/R4, padding ROM e finestre NWiFi potevano oltrepassare oggetti nativi o usare byte non inizializzati | span esatti, inizializzazione, capacità e progressi sono controllati prima dell'accesso; gli ultimi limiti validi restano accettati |
| Core, savestate | callback, FIFO, GPU, SPU, camera, AES, RTC e DMA potevano adottare combinazioni non rappresentabili | il caricamento è fail-closed su invarianti locali e incrociati; il precedente stato viene ripristinato quando il load semantico fallisce |
| Core, lavoro guest | Action Replay, DSP G.711, BMI NWiFi e rumble potevano imporre lavoro o accodamento non limitato | budget per frame/comando, quarantena e backpressure mantengono finito il lavoro senza alterare i casi validi adiacenti |
| NAND/DSiWare | letture brevi, dimensioni non allineate, titoli/copie senza tetto e scritture oltre capacità potevano propagare dati incompleti o consumare memoria/storage | I/O esatto, limiti 1 MiB–512 MiB, geometria settore e commit controllati; nessuna mutazione del save NAND sorgente in caso di errore |
| ROM/SAF | nomi provider, cache legacy, archivi e lanci esportati potevano collidere, sostituire ROM o leggere fino a EOF | identità digest, nomi di storage isolati, budget compressi/decompressi e validazione della copia effettiva chiudono i percorsi dimostrati |
| Cheat/camera/RA | conteggi aggregati, testo, immagini, memoria emulata e lease JNI non avevano tutti un limite complessivo | budget di documento/pixel/risposta, confini di regione e rilascio deterministico impediscono crescita e overread nei casi coperti |
| Lifecycle | enumerazione NAND e thread nativi potevano sopravvivere all'owner; update disabilitato poteva ancora contattare GitHub | contatori/lease seguono il ciclo di vita; il repository production update è un no-op |
| Build/release | artefatti Gradle e azioni annidate potevano sfuggire alla policy; un tag poteva raggiungere release production | metadata di verifica, pin immutabili, controllo CI, source/actor gate e ambiente production; protezione esterna dell'ambiente/tag non verificabile localmente |

Una verifica **successiva** e in sola lettura dell'API GitHub ha reso più
preciso il limite dei due finding release: `gh api
repos/Umberto-DEV/melonDS-android/environments` ha restituito zero ambienti;
`gh api repos/Umberto-DEV/melonDS-android/rulesets` ha restituito `[]`;
`gh api repos/rafaelvcaetano/melonDS-android/environments` ha elencato solo
`copilot`, senza `production-github-release` o `production-playstore`. La vecchia
API `tags/protection` ha risposto 404, dunque la protezione tag resta non
verificata. Nel fork entrambi i job sono esclusi dal gate
`github.repository == 'rafaelvcaetano/melonDS-android'`; sul codice destinato
all'upstream richiedono anche `github.actor == 'rafaelvcaetano'`. Il workflow
Play Store è ora manuale e usa `status: draft`; quello GitHub Release conserva
il trigger per tag autorizzato. I due ambienti production **non hanno una
protezione reviewer dimostrata**. I due finding restano quindi nella categoria
repository corretto / controllo esterno non completato, e nessuna release è
stata lanciata.

Il record R4 FAT divide-by-zero è non applicabile al target: Android non collega
uno storage SD al core R4, precondizione necessaria al percorso. Non viene
presentato come fix desktop o come prova che altri frontend siano immuni.

La revisione indipendente finale non ha trovato blocchi. Ha conservato questi
limiti: identità cache legacy ambigua soltanto per record versione 0 con stesso
nome; una destinazione SAF di export NAND può restare parziale se il provider
fallisce; alcuni savestate storici anomali ora sono rifiutati; il parcel Android
transitorio non ha un proprio numero di formato; un provider può mutare dopo la
validazione; protezioni GitHub esterne e provenienza indipendente dei checksum
Gradle non sono osservabili dal repository. La copia temporanea e i limiti nativi
vengono comunque rivalidati ai rispettivi sink.

## Prove prima/dopo e verifiche finali

I test sorgente di regressione sono conservati. RED indica un'esecuzione
pre-fix soltanto dove il report/test l'ha realmente prodotta; gli altri casi
restano source-backed con chiamante e controllo negativo, non RED dinamici.

| Livello | Risultato sulla coppia finale | Limite |
| --- | --- | --- |
| Host security | `bash tools/host-tests/security/run.sh`: pass | macOS host, non frontend Mac e non runtime Android |
| Core sanitizer | `BUILD_JOBS=2 tools/core-host-sanitize.sh`: pass | warning libc++ di piattaforma non sommati ai passaggi |
| Action Replay reale | `tools/host-tests/cheats/run.sh`: pass, incluso budget esatto | fixture sintetica/homebrew |
| JVM/API/build/lint | Gradle finale: BUILD SUCCESSFUL, 211 task (34 eseguiti, 177 up-to-date); 55 suite XML, 346 test JVM/API, zero failure/error/skip; lint completato | warning Kotlin/C++/manifest e nome root registrati; i task up-to-date usano output della stessa coppia sorgente |
| Android NDK 4 KiB | 11 eseguibili API35 ARM64 della coppia finale, tutti pass; `emulator-5556`, page size 4096 | componenti target, non ART |
| Android NDK 16 KiB | gli stessi 11, tutti pass; `emulator-5554`, page size 16384 | componenti target, non dispositivo fisico |
| ART/provider | Sul binario di test finale: suite default 55 casi, 52 pass e 3 skip opt-in previsti; WorkManager reale separato 1 pass; zero fallimenti | debug/test APK `.dev`, non APK ottimizzato `.perf`; UI capture e fallback GLES opt-in non ripetuti in questo ultimo ciclo |
| Integrità CI | checker locale pass; [CI core finale](https://github.com/Umberto-DEV/melonDS-android-lib/actions/runs/41704485288) verde sui runner x86-64 e ARM64; [CI app finale](https://github.com/Umberto-DEV/melonDS-android/actions/runs/41704565464) verde nei job JVM/lint/ARM64 debug e native sanitizer Linux x86-64/ARM64 | il job legacy `build` risulta skipped, distinto dai tre job eseguiti |
| Sorgenti | `git diff --check` app/core pass; commit core `ac5e6ba0`, app `0018cddb` su `main` dei fork | `AGENTS.md` e materiale storico locale preesistente restano fuori dai commit |

I log finali e i piccoli report Gradle sono conservati nel dossier locale
`local/notes/pr-review-2026-11-03/final-verification/`. I comandi NDK sono
`BUILD_JOBS=2 tools/android-tests/build.sh --abi arm64-v8a` e, per ciascuno
degli 11 nomi in `tools/android-tests/build.sh`,
`tools/android-tests/run.sh --serial emulator-5554|emulator-5556 --test NOME`.
L'APK di test `.dev` e il test APK sono stati reinstallati con `adb -s emulator-5554
install -r -t --no-incremental` prima di `am instrument -w -r`; il caso
WorkManager ha usato `-e realWorkManager true`. I tre skip ART sono il controllo di cancellazione WorkManager che richiede opt-in,
la cattura UI cheat e il fallback GLES reale. Il caso WorkManager è stato poi
eseguito isolato con `-e realWorkManager true` ed è passato. Un primo fallimento del test BIOS
era un difetto dell'harness: la produzione ora apre la sorgente tre volte per
misura, validazione e copia; il provider bloccava ancora la seconda. Il test è
stato corretto per bloccare la terza apertura, poi è passato isolato e nella
suite completa. Non è contato come bug di produzione.

### Revisione successiva al primo push

Il primo push ha avviato [Core checks](https://github.com/Umberto-DEV/melonDS-android-lib/actions/runs/41702422811),
verde sui runner Linux x86-64 e ARM64, e la
[CI app](https://github.com/Umberto-DEV/melonDS-android/actions/runs/41702443735).
Quest'ultima ha dato esiti separati: native sanitizer ARM64 verde; job JVM/lint/APK
ferma prima dei test perché la verifica stretta Gradle non conosceva
`aapt2-9.3.1-15703166-linux.jar`; native sanitizer x86-64 rosso nel controllo
Thumb/DTCM JIT, con valore vecchio dopo la riscrittura del literal. Il checksum
SHA-256 `e772a3dae8354764f1b0793903218427f483982445207f2e4ffc8c2026755bd4`
è stato calcolato scaricando l'artefatto di 2.369.543 byte da Google Maven e
aggiunto a `gradle/verification-metadata.xml`; il checker CI locale resta verde.
Il backend x86-64 ora rifiuta il folding di indirizzi senza owner di
invalidazione, come già faceva quello AArch64. È un'omissione preesistente della
correzione JIT funzionale: la CI Linux iniziale è RED reale; il controllo sul
commit `ac5e6ba0` è GREEN nella [CI core](https://github.com/Umberto-DEV/melonDS-android-lib/actions/runs/41704485288)
e nella [CI app](https://github.com/Umberto-DEV/melonDS-android/actions/runs/41704565464).
La prova Linux x86-64 è distinta dall'esecuzione Android ARM64.

Una review dei confini della seconda remediation ha inoltre trovato due
correzioni adiacenti e una segnalazione smentita:

| Caso | Prima | Correzione / controprova | Prova e limite |
| --- | --- | --- | --- |
| G.711, finestra ARM9 NWRAM | Il range nominale poteva superare `0x04000000` e far validare un byte diretto a un'altra regione ARM9 | La validazione interseca il range con l'apertura reale `[0x03000000, 0x04000000)`; il limite esatto resta valido, quello oltre è rifiutato | Test helper ASan/UBSan RED exit 1, GREEN 29 controlli; compilazione `G711Ucode.cpp`; comando reale e caso source-to-sink da conservare, nessuna esecuzione G.711 end-to-end su Android |
| Action Replay, fairness del budget | Un cheat con esattamente 1.048.576 operazioni consumava il frame a ogni giro e il successivo restava indefinitamente differito | Ripresa dal cheat differito al frame successivo; IRQ ripetute nello stesso frame non rinnovano il budget e `Enabled` resta coerente col frontend | `bash tools/host-tests/cheats/run.sh frame-fairness` RED exit 10, poi GREEN; 14 modalità ASan/UBSan verdi fuori dal sandbox, dove `shm_open` è disponibile |
| Timestamp DSP futuro | La review ipotizzava che un timestamp oltre ARM9 fosse accettato e rendesse il DSP silente | Il codice già richiede `DSPTimestamp <= ARM9Timestamp` e rifiuta backlog oltre `2^24`, indipendentemente dal reset | `NativeRuntimeBoundsTest` ASan/UBSan: 13 controlli verdi, incluso timestamp futuro; nessuna patch e nessun nuovo bug contato |

Questi controlli non sono occurrence aggiuntive della scansione. Il JIT x86-64
è una lacuna funzionale emersa dalla CI dopo il push; G.711 e Action Replay
raffinano due confini già nel registro. Non sono tre nuove occurrence Security.

## APK 2.1.8/49

Poiché sono presenti nuove correzioni accettate, la versione è avanzata a
2.1.8/49 senza cambiare package o firma. L'identità è stata letta dall'APK, non
soltanto dal Gradle:

| Campo | Valore |
| --- | --- |
| Percorso canonico | `local/notes/pr-review-2026-11-03/candidate/melonDS-2.1.8-nightly-profiling.apk` |
| Byte | 85.676.746 |
| SHA-256 | `195b5748fc59bac74eadee148f0a1a7b440e47b51dcc5f70fdcd384e855932cb` |
| Package | `me.magnum.melonds.nightly.perf` |
| Versione | `2.1.8 GH (NIGHTLY) (PROFILING)` / 49 |
| API | min 24, target/compile 37 |
| ABI | frontend nativo solo `arm64-v8a`; l'APK contiene piccole librerie terze anche per `armeabi-v7a` e `x86_64`, perciò `aapt` elenca tutte e tre |
| Firma | APK Signature Scheme v2; certificato SHA-256 `41ad82cd4365c73195128975cf0b920699f8833093507957dc2d0f9d6bb0b4b1` |
| Modalità | `RelWithDebInfo`, resource optimization; non debuggable e non testOnly |
| Revisione incorporata | app `0018cddb04278f2bca5054804de501a459eb1de9`, core gitlink `ac5e6ba0fb795b7aa8a43fdefed62eed8a37eff8` |

È un APK GitHub Nightly ottimizzato per il target ARM64. La presenza di librerie
terze x86/ARM32 non prova che l'emulatore funzioni su quelle ABI. Restano il
candidato corrente e il fallback 2.1.7/48 per questo target.

Firma, package, versionCode, minSdk, flag e librerie native sono stati riletti
dall'APK ricompilato. L'APK esatto è stato inoltre installato con `-r` sull'AVD
già popolato: UID e firstInstallTime sono rimasti invariati, il launcher reale
`RomListActivity` è ripartito da cold start e non è comparso un fatal o ANR
nella lettura limitata dei log. Dopo l'installazione del binario esatto, la ROM
homebrew sia diretta sia in ZIP è stata aperta nell'Activity reale: entrambe le
schermate hanno mostrato i colori attesi. Il controllo privato byte-per-byte
successivo all'ultimo `install -r` non è stato possibile: la richiesta `adb root`
sull'AVD è stata respinta dalla revisione automatica dei permessi. Restano la
prova integrale di upgrade con dati sintetici sul candidato precedente e il
controllo di UID/firstInstallTime/runtime sul binario esatto; non sono la stessa
prova. Un primo tentativo di avviare `MainActivity` ha fallito perché quella
classe non è il launcher: errore dell'harness, corretto usando il manifest.

## Aggiornamento conservativo sull'AVD

L'aggiornamento reale è stato provato su
`melonds-buglab-api35-arm64-16k`, seriale esplicito `emulator-5554`, Android 15,
ARM64, page size 16.384. Prima è stato installato 2.1.7/48 e popolato tramite i
percorsi reali con dati sintetici:

- database Room v8 di 114.688 byte, SHA-256
  `0507fd26539478f188feeda7503405f0821b8cec8704a3ce78dab85b98139e0a`,
  con database, gioco, cartella e cheat disabilitato;
- layout personalizzato SHA-256
  `1721da8f557ea0808cc637a1e0c594307d3193fcc657573c977e4a5759e5e7bf`;
- marker file SHA-256
  `72f6e094512f05a7911e3246e33e72c3732639083b244bf2387f9d1dae99b77e`;
- preferenza `upgrade_probe=keep-across-2.1.7-to-2.1.8`;
- directory SAF `Download/melonds-upgrade-probe`, grant persistente read/write
  con prefix per UID 10215.

`adb -s emulator-5554 install -r --no-incremental` ha restituito `Success`.
VersionCode è diventato 49, mentre UID e firstInstallTime sono rimasti invariati.
Subito dopo l'installazione gli hash di database, layout, marker e preferenze
erano identici e il grant era ancora persistente. Dopo il primo avvio 2.1.8:

- `PRAGMA integrity_check` era `ok`; righe database/cheat intatte;
- database, layout e marker erano byte-identici;
- le preferenze differivano solo per `last_version` 48→49 e timestamp update;
- marker, directory ROM e grant SAF mode `0x3` erano conservati;
- nessun `AndroidRuntime` fatal nel log limitato.

La fixture ROM è originale e generata localmente, non commerciale: 131.072 byte,
SHA-256 `e298b528668165047d8bb4d7dfa0df038ddb9a6d91289b6f7a84b55e8b2bd5f4`.
È stata avviata dalla libreria sia direttamente sia da ZIP sintetico SHA-256
`c502c25d14bd66ef5b59d88f99420de972d5ecd26676667462add61ad080f779`.
Le due fasi cromatiche complementari sono state osservate; HOME/ripresa, input
button/touch e lifecycle non hanno prodotto fatal, ANR o timeout nel log
limitato. Questa ROM non usa un save: Continue/SAVE/cold Continue non sono quindi
coperti e non vengono dichiarati passati.

Un reboot dell'AVD con snapshot/grafica ha lasciato ActivityManager non
disponibile. È stato arrestato soltanto quell'AVD e riavviato con `-no-snapshot`
e rendering software; userdata e prova di upgrade sono rimasti intatti. È un
problema dell'harness AVD, non un difetto attribuito a melonDS.

## Spazio e chiusura dei produttori

Il manifesto locale
`local/notes/pr-review-2026-11-03/second-campaign-deletion-manifest.json`
registra percorso, produttore, dimensione, hash quando pertinente e decisione.
Sono stati rimossi 2.248.740.864 byte allocati noti (circa 2,09 GiB): output
Gradle app/API riproducibili, fixture homebrew/ZIP temporanee e copie sintetiche
esterne dell'AVD. La directory root `build/` è stata ispezionata e **conservata**:
mescola binari rigenerabili con log, baseline, issue capture e prove storiche
uniche, quindi il nome non ne autorizza la cancellazione.

Subito dopo quella pulizia restavano circa 28 GiB liberi, saliti a circa 29 GiB
al controllo allora finale. La successiva ricompilazione del candidato ha
rigenerato output Gradle, poi ispezionati e puliti. Sono stati rimossi altri
2.658.540.241 byte noti: il precedente candidato 2.1.8 sostituito solo dopo
hash, intermedi app/API e il JAR aapt2 ufficiale usato per il checksum. Il
totale noto delle due pulizie è 4.907.281.105 byte; i piccoli report Gradle,
log Android, screenshot e fixture homebrew sono stati spostati nel dossier
canonico locale. L'ultimo `df -h` mostrava **18 GiB liberi**; il valore del
filesystem può oscillare e non coincide necessariamente con i byte allocati
dei singoli file.
Una pulizia storage separata e autorizzata da
un'altra lavorazione ha liberato 10,52 GB di video Apple; non è attribuita a
questa campagna e nessun suo repository è stato modificato qui. Gli AVD 4/16 KiB,
il daemon Gradle e il server ADB della campagna sono stati arrestati. I soli
due APK protetti, 2.1.8 corrente e 2.1.7 fallback, sono stati riletti dopo
la pulizia e mantengono gli hash documentati.

## Thor, pubblicazione e passo futuro

La Thor non è stata collegata o modificata. L'ultima installazione fisica
documentata resta 2.1.6/47; non è stato assunto che abbia 2.1.7. Nessun dato
originale, ROM commerciale, BIOS, firmware/NAND, save, database o grant personale
è stato modificato. Sono stati eseguiti commit e push soltanto sui `main` dei
due fork, come autorizzato successivamente. Non sono stati aperti PR o issue,
pubblicate release o inviate comunicazioni upstream.

Una futura prova Thor richiede nuova autorizzazione: rilettura package/versione,
certificato e UID; backup verificati e leggibili di save, NAND/firmware,
database/cheat, preferenze, layout e grant; hash prima/dopo; installazione solo
con `adb -s SERIAL install -r --no-incremental`; mai uninstall, clear-data o
cambio firma; poi Continue/SAVE/arresto/cold Continue su copie, lifecycle,
audio, input e renderer. La firma compatibile non sostituisce queste verifiche.

## Limiti

La copertura completa dichiarata è quella dell'inventario coordinato della
scansione, non una prova che ogni percorso sia privo di difetti. Una homebrew e
le suite sintetiche non certificano tutti i giochi. Non sono coperti da questa
campagna Thor, driver fisici, DSi HOME/quota reale, perdita di alimentazione,
provider ostile concorrente dopo l'ultima validazione, né le protezioni esterne
GitHub. Non si dichiara “nessun bug”, sicurezza completa o assenza generale di
rischio di perdita dati.
