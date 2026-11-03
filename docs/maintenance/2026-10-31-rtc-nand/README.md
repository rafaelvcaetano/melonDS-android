# RTC e integrità dell'import NAND — 31 ottobre 2026

Verifica successiva: [candidata 2.1.6, correzioni aggiuntive e controlli finali](../2026-10-31-nightly-2.1.6/README.md).
Le identità e i risultati di questo rapporto restano riferiti al primo lotto.

Primo lotto del [mandato](../NEW-CHAT-PROMPT.md), con
[piano e confini](PLAN.md). Due piste iniziali: RTC #2653 e quota DSi #1651.
Non è un nuovo censimento. Risultato: **due cause corrette nel core**, nessuna
nuova modifica RTC; quota logica e boot HOME restano aperti.

## Identità e livelli di prova

- App `main`, `1cf4fa6adf3c7a8621734754d907ea0d27824fe9`;
  core `main`, `fd7582400bb7e35fe0d36dbb0c89b6766c1a8f2e` più diff non committata
  in `src/DSi_NAND.cpp`. Nessun cambio di branch, commit, push o PR.
- Le modifiche iniziali di `AGENTS.md`, il piano cheat e il dossier ventuno
  non sono stati modificati. Il core era pulito all'avvio.
- APK compilata: `me.magnum.melonds.nightly.dev`, GitHubNightlyDebug, ARM64,
  versionCode 46 / `2.1.5 GH (NIGHTLY)`, min API 24. SHA-256:
  `46bc7c6b179065a3a89ea0ffd461264fb7422357804c01d30204584583c5b880`.
  Certificato Android Debug SHA-256:
  `41ad82cd4365c73195128975cf0b920699f8833093507957dc2d0f9d6bb0b4b1`.
  **Compilata, non installata né eseguita.** Nessuna equivalenza con APK storiche.
- Eseguibili NDK 28.0.13004108, API minima 24, ARM64, libc++ statico e UBSan
  non recuperabile. Eseguiti sui due AVD dedicati buglab Android 15/API 35:
  `emulator-5580`, pagine 4096; `emulator-5582`, pagine 16384.
  Nessuna installazione APK. Software/OpenGL/Compute non sono attivati da questi
  nuovi test; RTC non esegue frame e non usa JIT guest o fastmem come oracolo.
- Host: macOS ARM64, ASan/UBSan; risultato separato da Android. Identità/hash,
  patch, ricerche API e log sotto `local/notes/android-audit-2026-10-31/`.
  `identities.json` identifica sorgenti ed eseguibili; non contiene dati personali.

## Registro dei casi

| Caso | Stato e prova | Risultato/limite |
| --- | --- | --- |
| RTC, shared core #2653; app #1604/#1652 | Già coperto localmente per la politica opt-in alla ripresa; sei controlli host e NDK su entrambi gli AVD | Nessuna nuova correzione; non certifica eventi Pokémon, cambio ora durante esecuzione, Activity/JNI o ripristino dopo morte del processo |
| DSi #1651/#1099: quota logica | Segnalato, non riprodotto localmente | Mancano una fixture bootabile e confronto HOME; non aggiunta una soglia ipotetica di 1024 blocchi |
| NAND-WRITE-01: successo con scrittura parziale/errore | Riprodotto sul core originale; corretto e verificato sul target NDK dichiarato | Core `ImportFile`, ticket, salvataggi, banner e TMD controllano esito, conteggio e chiusura; caller Android già elimina un nuovo titolo fallito |
| NAND-ALIGN-01: store disallineati nell'header save | UBSan originale host e Android, poi controllo stretto verde | Header FAT scritto per byte, stessa codifica little endian; non è una prova di crash su telefono senza sanitizer |
| App #1678: riferimenti JNI | Correzione preesistente conservata, controlli host/NDK verdi | Nessuna patch riapplicata; il modello JNI non è ART/CheckJNI |

### RTC: confutazione del nuovo difetto alla ripresa

[Issue #2653](https://github.com/melonDS-emu/melonDS/issues/2653), aperta,
4rt-urit0 (6 maggio); Killrmemz distingue nel commento del 17 luglio la pausa
dalla modifica manuale dell'ora. Thread riletto integralmente. Anche
[#1604](https://github.com/rafaelvcaetano/melonDS-android/issues/1604) resta aperta:
il workaround riportato è salvare nel gioco e riavviare, non caricare uno stato.

[PR #1664](https://github.com/rafaelvcaetano/melonDS-android/pull/1664) di Komalis
è aperta, non unita; letti tutti i 14 commenti e la patch attuale. Il maintainer
chiede il segnale di ripresa dell'emulatore e osserva che il lid da solo non
ferma l'emulazione. Il fork ha già tale percorso, con opzione disabilitata per
default e avanzamento solo oltre 30 secondi. Il
[commit upstream di Arisotura](https://github.com/melonDS-emu/melonDS/commit/0a65c3785f88c3d3f057e9cee3145ce103a3382b)
sincronizza invece ogni frame: non è stato copiato. Le discussioni sui salti
temporali non giustificano una garanzia generale sugli eventi dei giochi.

Caller: Activity/ViewModel → manager → JNI `resumeEmulation`, sotto mutex del
thread emulazione → `MelonDSAndroid::resume` → `onResumed` → `syncRTC` → RTC.
Il test compila i tre corpi reali, senza sostituire RTC con una decisione finta;
adatta solo proprietà dell'istanza/configurazione. Il puro `RtcSyncTest` resta.

Due tentativi di confutazione: (1) opzione spenta, l'ora guest resta invariata;
(2) guest avanti di un'ora, non viene riportato indietro. Controlli vicini:
deriva di cinque secondi e venti riprese non riscrivono il clock. Due ritardi
separati di un'ora/dieci minuti vengono recuperati leggendo i registri reali.
Questo esclude un nuovo errore nel corpo della sincronizzazione, non un evento
Android mai consegnato. La segnalazione complessiva rimane parziale.

[Shared #1945](https://github.com/melonDS-emu/melonDS/issues/1945), aperta, riguarda
12/24 ore e inizializzazione dei seed; letti i nove commenti. Il costruttore RTC
locale imposta già il bit 24h. Nessuna regressione di seed è stata dedotta da
quel thread, e nessuna ROM è stata eseguita per questo caso.

### NAND: primo errore, controlli e correzione

[App #1651](https://github.com/rafaelvcaetano/melonDS-android/issues/1651), aperta,
conpoko, 24 luglio, zero commenti; [#1099](https://github.com/rafaelvcaetano/melonDS-android/issues/1099),
aperta, un commento. La diagnosi di quota logica resta un'ipotesi. Letti anche
[shared #1641](https://github.com/melonDS-emu/melonDS/issues/1641), chiusa dopo
liberazione di spazio, e [#2737](https://github.com/melonDS-emu/melonDS/issues/2737),
aperta, dove spazio, regione e Unlaunch sono spiegazioni alternative non risolte.
Ricerca in issue/PR app, Android-lib e core, comprese chiuse e workaround;
confrontati `DSi_NAND.cpp` di entrambi gli upstream `master` e dieci commit della
cronologia del file. I controlli mancavano anche lì. Il fix CTR #2197 è già
presente: non riapplicato. La PR R8 #1046 non è una soluzione alla quota.

Percorso raggiungibile: DSi manager → `MelonDSiNand.importTitle` → JNI →
`NANDMount::ImportTitle(path)` → struttura titolo → `ImportFile`. Quando FAT
esaurisce i cluster, `f_write` può restituire `FR_OK` con meno byte richiesti;
il codice originale ignorava entrambi. Il JNI vedeva `true`, quindi saltava la
pulizia già prevista per `false`. Non è stato introdotto un nuovo rollback.

Fixture deterministica: FAT 4 MiB, NAND cifrata con identità tutta sintetica,
titolo `00030004/54455354`, payload 65536 byte, sentinel indipendente 1024 byte.
Lasciare 16384 byte liberi e importare. **Originale: ritorno true, .app persistita
11776/65536 byte**, verificata dopo smontaggio/rimontaggio; con save richiesto
può essere zero byte. Nessun codice di scrittura FAT è duplicato dal test.

Confutazioni: (1) stesso input con spazio sufficiente persiste esattamente;
(2) errore sia nel percorso file usato da Android sia nell'overload memoria,
quindi non dipende da SAF o dall'URI. Il sentinel resta identico. Sedici scenari
incrociano due ingressi, quattro forme di save e due stati di spazio. Originale:
16 asserzioni fallite; corretto: zero. Dopo `DeleteTitle` esplicito, directory e
ticket devono mancare; liberare il filler e ritentare deve produrre byte esatti.

Tre ulteriori controlli, con errore solo al confine Platform e FAT/crypto reali:
lettura sorgente fallita, scrittura diretta fallita e scrittura dell'ultimo settore
bufferizzato durante close. Sul sorgente originale **tre falsi successi**; dopo
la correzione tutti propagano il fallimento. Source/sentinel sopravvivono, retry
e rimontaggio restituiscono i contenuti attesi. Il percorso JNI con core reale
e ART non è eseguito: la sua pulizia è verificata separatamente con il modello
JNI esistente e tracciata nel sorgente, senza sommare le prove come integrazione.

Il primo scenario con save ha inoltre fermato UBSan su store `u16` a offset
0x00B; altri store disallineati erano a 0x011/0x013/0x027. La correzione scrive
campi little endian per byte. Lunghezza, seriale, firma FAT e campi indipendenti
sono riletti dopo rimontaggio. Android originale abortisce con 134; corretto passa.

## Prove eseguite e accettazione

- `bash tools/host-tests.sh`: dieci target PASS, `JitDifferentialTest` SKIP
  perché nessuna ROM è stata fornita; `nand-fat` comprende 19 scenari.
  ASan/UBSan stretti; leak detection disabilitata nel banco core, come già previsto.
- `sh tools/host-tests/nand-jni/run.sh`: due eseguibili verdi, inclusi errore,
  pulizia, metadati, contenuti e lifetime dei riferimenti. Confine JNI simulato.
- JDK21, `testGitHubNightlyDebugUnitTest`, `assembleGitHubNightlyDebug`,
  `lintGitHubNightlyDebug`, ARM64: BUILD SUCCESSFUL; **267/267 JVM**, zero skip;
  lint **0 errori, 474 warning**. Rimangono warning compilatore preesistenti.
- `bash tools/android-tests/build.sh --abi arm64-v8a`: otto eseguibili compilati.
  Tutti e otto eseguiti separatamente con exit 0 su **API35/4KiB e API35/16KiB**.
  Non sono otto test ART. RTC stampa anche la diagnostica ftruncate del banco
  fastmem; nessuna affermazione sulle mappature JIT deriva dal test RTC.
- Originale NDK compilato da `git show fd758240:src/DSi_NAND.cpp`: falso successo
  riprodotto, poi abort UBSan sul save. Log RED conservato, binario riproducibile
  eliminabile; nessuna accettazione verde attribuita a quel binario.
- Revisione indipendente: nessun blocco di produzione; corretti il rischio di
  core host non aggiornato e i define JIT discordanti su host Intel macOS.
  Quest'ultimo è un aggiustamento del banco, non un difetto frontend Mac cercato.
  I tre test I/O sono stati aggiunti dopo la revisione e verificati RED/GREEN.
- `git diff --check` in app e core passa. Correzioni lasciate revisionabili su main.

Non eseguiti: ART/CheckJNI completo con NAND reale, avvio HOME, esecuzione ROM,
API33, ABI x86_64/32bit, GPU/lifecycle fisico, perdita di alimentazione e scrittori
esterni. Non ci sono prove di risparmio energetico o di compatibilità universale.

## Prossimi esperimenti utili

1. Quota: su una **copia autorizzata e bootabile** della stessa NAND, registrare
   cluster FAT, blocchi HOME, title/save/TMD/ticket e regione, variare un titolo
   sintetico per volta e verificare HOME prima/dopo. Non dedurre la quota dal
   solo spazio fisico. Nessun input personale è stato cercato o copiato qui.
2. RTC: programma DS originale che legge i registri prima/dopo pause menu,
   screen-off, ripresa con overlay e checkpoint dopo morte del processo; timeline
   Activity/JNI e impostazione sync esplicita. Un fallimento distinguerà segnale
   perso, registri e cache del guest senza attribuirlo automaticamente alla ROM.
3. Integrità: prova ART combinata del nuovo errore core e pulizia JNI su NAND
   sintetica; sostituzione di save già esistente resta una verifica distinta.

## Dati e conservazione

Nessuna ROM, BIOS, firmware, NAND, save, DB, impostazione o firma personale è
stata cambiata. Nessun accesso di scrittura a Sacred Gold Plus. Nessuna pulizia
di cartelle build in blocco; solo intermedi compilati in questa esecuzione e
fixture temporanee identificati individualmente nel manifesto locale. APK e
binari verdi hanno una sola copia canonica nei percorsi build; fallback storici
esterni non toccati. Log piccoli, sorgente originale e hash sono conservati.
Spazio prima ~43 GiB; consuntivo e cancellazioni nel manifesto locale.

Consuntivo: eliminati **568 file host**, 636624896 byte allocati (**607,1 MiB**),
più una fixture sintetica da 65536 byte sull'AVD. Sono oggetti compiler di questa
esecuzione, il binario RED riproducibile e l'input rimasto dall'abort; nessuna
directory rimossa in blocco. Gli hash di APK, otto binari verdi, sorgente core e
AGENTS sono identici prima/dopo la pulizia. Restano circa **40,6 GiB** liberi.
L'opzione CMake per il sorgente originale è stata svuotata; i due AVD avviati
per questa campagna vengono chiusi dopo le prove. Manifesto completo:
`local/notes/android-audit-2026-10-31/deletion-manifest.json`.
