# Persistenza e lifecycle Android — correzioni e limiti, 31 ottobre 2026

Verifica successiva: [candidata 2.1.6 e dieci test NDK su API35/4KiB/16KiB](../2026-10-31-nightly-2.1.6/README.md).
I risultati seguenti restano quelli del lotto precedente al confezionamento.

Questo lotto prosegue il dossier esplorativo locale
`local/notes/android-audit-2026-10-31/README.md` (ignorato da Git) e il
[lotto NAND/RTC precedente](../2026-10-31-rtc-nand/README.md). I quattro
candidati iniziali restano distinti. La quota logica DSi e il boot HOME non
sono prove della sostituzione dei save. Nessun dato personale o immagine NAND
è stato usato: i test FAT creano un volume cifrato sintetico in un file anonimo.

## Identità e proprietà del lavoro

- App `main` a `1cf4fa6adf3c7a8621734754d907ea0d27824fe9`, avanti di sei
  commit rispetto a `origin/main` al controllo. Core `main` a
  `fd7582400bb7e35fe0d36dbb0c89b6766c1a8f2e`. La diff locale NAND del
  lotto precedente era presente; non è data per pubblicata sul remoto.
- Codice app analizzato: `MelonDSNandJNI.cpp` SHA-256
  `3bbb3a902bf4ad9fe08ba1e11eac3f3351f57155a3610ff71da113aa492a1c71`.
  Codice core: `DSi_NAND.cpp` SHA-256
  `f6825bdd87ee02d655821db34c82d9826840c55889cbd6b8e777925f5fefec4c`.
  La diff binaria app sul solo JNI ha SHA-256
  `2b10589a9517f4159ada7c9ece00eda93bb0b615f1dfe9cb0548bba00766b3cc`;
  la diff core complessiva, che include la correzione precedente, ha SHA-256
  `2132ef43030161c1d28f124db511b6d0400061241f8bdb1db06a3c0834f3f2c0`.
- Sono stati modificati in questo lotto soltanto
  `app/src/main/cpp/MelonDSNandJNI.cpp`,
  `melonDS-android-lib/src/DSi_NAND.cpp`, i due nuovi test
  `tools/host-tests/nand-jni/UtfLeaseTest.cpp` e
  `tools/host-tests/nand-fat/ExistingSaveReplacementTest.cpp`, e gli script
  `tools/host-tests/nand-jni/run.sh`, `tools/host-tests/nand-fat/run.sh`,
  `tools/android-tests/CMakeLists.txt`, `build.sh`, `run.sh`, oltre a questo
  rapporto, l'indice e la riga pertinente del registro. Le altre modifiche
  locali, incluse le due bozze PR, appartengono ad altri lotti/chat.

## C1 — sostituzione di un save NAND esistente

**Origine e caller.** `NANDMount::ImportTitleData` rimuoveva `public.sav`,
`private.sav` o `banner.sav` prima di aprire la sorgente. L'ordine risale al
commit core di Arisotura `235da420c8ac0bcec5a21db6f3cce4ae190fb7c5`
(24 agosto 2021). Il percorso Android raggiungibile è
`DSiWareManagerViewModel.importDSiWareTitleFile` →
`AndroidDSiNandManager.importTitleFile` → JNI → `NANDMount::ImportTitleData`.
Il mutex del manager serializza l'operazione, ma non rende transazionale FAT.

**Atteso, RED, controlli.** Con un save precedente di 65.536 byte `0x51`,
un'origine assente deve dare `false` e conservare entry e byte. Sul core reale,
dopo un remount il risultato precedente era `FR_NO_FILE=4`; il test permanente
ha registrato `FAIL: missing source preserves prior save entry`. Un fault di
lettura conservava l'entry ma ne troncava i byte; il medesimo test ha registrato
`FAIL: source read failure preserves old bytes`. La seed era stata riletta
esattamente prima dei fault. Il test precedente `NandFatTest` passava, quindi
la causa è separata dalle già corrette verifiche di scrittura e dagli errori
di inizializzazione del titolo. L'originale e il file di log RED restano
identificati nel dossier locale.

**Correzione e GREEN.** `ImportTitleData` importa in un file FAT temporaneo;
solo dopo una copia riuscita sposta il save precedente in un backup e rinomina
quello nuovo. Se il secondo rename fallisce, tenta il rollback. Un invocazione
successiva ripristina il backup lasciato da un'interruzione dopo il primo
rename. La prova sul core/FatFs reale controlla dopo remount: origine mancante,
lettura fallita, scrittura NAND fallita, spazio esaurito, retry riuscito,
interruzione simulata dopo il rename del vecchio save e percorsi equivalenti
private/banner. `ExistingSaveReplacementTest: failures=0`; `NandFatTest:
0 failure(s)`. L'artefatto NDK ARM64 del nuovo test ha SHA-256
`a098ee28e9b07f35e74a8505d73b5e5b4c673cbbc9e1fc6627846aef3b10a79e`.

**Confutazioni e limiti.** Il test ha escluso seed assente, rimonta stale e
semplice mancata segnalazione dell'errore. Il primo tentativo GREEN aveva un
difetto del banco: `/dev/fd` condivide l'offset del file anonimo, per cui il
retry leggeva da EOF. Il test ora esegue `rewind` prima di ogni tentativo;
questo errore del banco è separato dal RED del prodotto. FatFs `f_rename`
modifica più entry e non offre atomicità contro power loss nel mezzo della
rinomina; un errore I/O proprio durante commit/rollback richiede ancora fault
injection e readback dell'immagine. Non è stata eseguita la nuova prova su ART
con un provider SAF che fallisce mid-read. Il prossimo esperimento è quella
prova, con copia sintetica della NAND e controllo dei byte prima/dopo.

## C2 — lease UTF di import/export JNI

**Origine e caller.** `MelonDSiNand_importTitleFile` e `exportTitleFile`
rilasciavano `GetStringUTFChars` soltanto se `isCopy=true`. Quel flag descrive
la rappresentazione dei caratteri, non annulla l'obbligo JNI di rilascio.
Entrambi sono raggiungibili dal manager DSiWare tramite i metodi Java nativi.

**Atteso, RED, controlli.** Ogni acquisizione riuscita deve avere un solo
rilascio. Il test compila l'unità di traduzione JNI di produzione con un
`JNIEnv` strumentato che restituisce `isCopy=false`: i due metodi restituivano
successo dal boundary, ma il conteggio era `acquired=2`, `released=0`. La suite
JNI precedente restava verde, escludendo un guasto generalizzato ai local ref
o al boundary del titolo. Un secondo RED ha fatto restituire `nullptr`
dall'acquisizione: l'import continuava e restituiva successo, benché non ci
fosse alcuna stringa acquisita.

**Correzione e GREEN.** I due percorsi ora passano `nullptr` per il flag,
ritornano `false` se l'acquisizione fallisce e rilasciano ogni puntatore
acquisito dopo la chiamata core. `UtfLeaseTest` e l'intera
suite JNI passano sotto ASan/UBSan; il test compilato NDK ARM64 ha SHA-256
`9a47475e84c82197ad674bff6b26930990c30a38250ac14fa67887325d13d3b3`.
È una prova host del contratto JNI e una compilazione Android NDK, non una
misura ART di pin/lease. Il prossimo esperimento è un loop import/export in
una app diagnostica con CheckJNI/profiler, senza dati personali.

## C3 e C4 — candidati non corretti

- **C3, Surface/EGL e issue app #1665:** il test con la classe Kotlin di
  produzione dimostra che `destroy(A); create(B)` tra due frame lascia
  l'EGLSurface creato per A, mentre `A→A` e `A→null→B` sono controlli verdi.
  La mutation mirata del ramo null ha reso rosso anche il secondo controllo.
  La causa del black screen #1665 resta incerta: mancano callback/frame reali
  su Android 9 e risultati `eglSwapBuffers`. La PR app #1657 di dysprosium
  affronta la pausa/visibilità, non traccia la generazione della Surface.
  Nessuna patch di rendering in questo lotto. Prossima prova: schedule #1665
  su AVD Android 9 con log limitato di generazione Surface, create/destroy/swap.
- **C4, close NAND concorrente:** `closeNand()` evita il mutex usato da
  import/export; la cancellazione dello scope non attende necessariamente il
  JNI in corso. Restano forti guardie UI (dialogo non dismissibile,
  ViewModel stabile nei cambi configurazione) e manca uno schedule ART che
  dimostri use-after-free. Nessuna patch. Prossima prova: provider diagnostico
  con latch dentro l'import, task removal e CheckJNI/ASan, più ordine negativo
  import-completato-prima-del-close.

## Verifiche e copertura effettiva

- `bash tools/host-tests/nand-fat/run.sh`: ASan/UBSan, suite precedente
  `0 failure(s)`, nuovo test `0 failure(s)`; log GREEN
  `local/notes/android-audit-2026-10-31/replacement-green-final.log`.
- `sh tools/host-tests/nand-jni/run.sh`: nuovo test RED prima della patch,
  poi tutta la suite GREEN, sei scenari StorageNand compresi.
- `bash tools/android-tests/build.sh --abi arm64-v8a`: compilazione NDK dei
  dieci eseguibili, inclusi i due nuovi, riuscita. Nessun eseguibile nuovo è
  stato eseguito su Android; nessun ADB o APK installato da questo lotto.
- JDK 21, `testGitHubNightlyDebugUnitTest`, `assembleGitHubNightlyDebug`,
  `lintGitHubNightlyDebug`: BUILD SUCCESSFUL; 267 test, zero failure/skip;
  lint 0 errori e 474 warning. Il primo lancio senza `JAVA_HOME` e il secondo
  senza accesso alla cache Gradle erano errori d'ambiente; la ripetizione con
  il JDK e la cache installati è riuscita. La build Debug non prova il profilo
  ottimizzato `.perf` né il runtime su dispositivo. L'APK diagnostico locale
  `app/build/outputs/apk/gitHubNightly/debug/app-gitHub-nightly-debug.apk`
  ha SHA-256 `83f09809ee7f9e3ea933af034034c6af870b83c529781d42b7264e88d4da4260`;
  non è la candidata finale da installare sulla Thor.
- `git diff --check` app/core: riuscito. Nessun dispositivo fisico, ART/app,
  installazione personale, push, PR o release in questo lotto.

Ricerca upstream: l'[issue app #1665](https://github.com/rafaelvcaetano/melonDS-android/issues/1665)
è aperta; la [PR #1657](https://github.com/rafaelvcaetano/melonDS-android/pull/1657)
è aperta; l'[issue core #1651](https://github.com/melonDS-emu/melonDS/issues/1651)
rimane contesto DSi e non una prova della stessa causa; la
[PR core #1558](https://github.com/melonDS-emu/melonDS/pull/1558) di
Pk11/Epicpkmn11, già unita, aggiunge filtri file al title manager Qt.
Le ricerche Android-lib non hanno trovato una correzione equivalente. Le
correzioni locali non chiudono automaticamente alcuna issue upstream.

## Stato e ordine delle prossime prove

| Priorità | Caso | Stato locale | Prossima prova discriminante |
| --- | --- | --- | --- |
| 1 | C1, perdita del save esistente | Confermato host su core reale; corretto host, NDK compilato | ART con provider SAF che fallisce e readback della NAND; poi fault durante rename |
| 2 | C3, Surface/EGL #1665 | Sequenza RED nella logica JVM; causa Android ancora incerta | Android 9 con ordine callback/frame e swap EGL registrati |
| 3 | C4, chiusura NAND concorrente | Sospetto da sorgente, non riprodotto | ART con import bloccato, task removal e CheckJNI/ASan |
| 4 | C2, lease JNI | RED/GREEN host del contratto; corretto, NDK compilato | ART CheckJNI/profiler su ciclo import/export |

Le correzioni NAND del lotto precedente restano controlli di regressione:
`NandFatTest` è verde e nessuna nuova causa è stata attribuita alla quota
logica DSi o al boot HOME. Nessun candidato C3/C4 è stato promosso a fix o
a finding di vulnerabilità.

Lo `security-scan` del dossier esplorativo ha copertura parziale sul codice
app. Il `security-diff-scan` aggiornato sul solo file core modificato,
`29b5e4d5-b1ff-48c4-a942-98744f855f49`, ha completato l'inventario della
diff (1/1 file) senza finding di sicurezza. È un controllo offline del
codice, non una riproduzione Android. Nessuno dei difetti funzionali
dimostrati è stato classificato automaticamente come vulnerabilità.
