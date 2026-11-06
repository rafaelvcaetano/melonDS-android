# melonDS Android: integrazioni, prima/dopo e proposte upstream

Riferimento funzionale verificato: app `89053f0fd96ef2bc616849deb15900e1d6b09f43`,
core `fd7582400bb7e35fe0d36dbb0c89b6766c1a8f2e`. Entrambi su `main`.
Il confronto storico è con gli antenati upstream app `c42995ca` e lib `431ab4bd`;
la successiva [verifica della Nightly ufficiale del 29 ottobre](../2026-10-29-nightly-comparison/README.md)
conferma che sono anche le revisioni dell'ultima APK pubblicata e dei `master`
upstream a quella data. Questo resoconto unisce le campagne precedenti e la
rifinitura finale, distinguendone la provenienza.

**Integrato nel fork non significa già unito upstream.** Nessuna nuova PR è
stata pubblicata. Il 29 ottobre la 2.1.5 è stata installata sulla Thor come
[aggiornamento con conservazione dei dati e prove fisiche](../2026-10-29-thor-update/README.md).
I risultati successivi sono separati dalle prove originarie qui riportate. Lo stato GitHub
delle voci qui collegate è stato ricontrollato tramite API; data e metadati sono
in [GITHUB-STATUS.json](GITHUB-STATUS.json). La verifica è mirata, non aggiorna
silenziosamente tutte le 246 issue del censimento.

## 1. Funzioni aggiunte nell'app

| Funzione | Prima | Ora nel fork | Controlli e limiti |
| --- | --- | --- | --- |
| Configurazione WFC/DNS | Mancava una schermata per gestire direttamente le connessioni firmware | Tre slot, DNS primario/secondario, abilita/disabilita dal menu pausa; scrittura DS/DSi | Test WfcAccessPointSlot/WfcSettingsStorage e checksum; precedente prova Android del firmware modificato. Non certifica server, trade o GTS. |
| Backup impostazioni e layout | Implementazione già upstream, nascosta da un flag disattivato | Funzione resa accessibile; cache controller aggiornata dopo il ripristino | SettingsBackupManagerTest; conservazione dei valori/layout. Corrisponde alla funzione richiesta in #264; non è un'implementazione nuova da zero. |
| Configurazione guidata BIOS | Selezione cartella e messaggi poco utili; copie e validità non sufficientemente protette | Riconoscimento e copia guidata; risultati DS/DSi separati, verifica reale prima delle preferenze, Retry ed errori nominati | 30 test BIOS JVM, inclusi Fragment/verificatore/preferenze, e 4 casi SAF/Binder Android. Non autentica i dump. |
| Sincronizzazione orologio opzionale | Il tempo emulato poteva non recuperare il tempo trascorso fuori dal gioco | Opzione disattivata per default, correzione soltanto in avanti | Policy RTC con test host; non attesta ogni evento temporale di ogni gioco né tutti i casi lid manuale. |
| Fast-forward tenuto premuto, combinazioni e mute | Mancavano queste opzioni di controllo | Hold separato dal toggle, hotkey composte, mute opzionale | Adattamenti attribuiti a #1608/#1606/#1625; test input/policy. Il mute è una funzione, non una correzione della fedeltà audio. |
| Screen-off e coperchio DS | La pausa poteva interrompere la sequenza lid del gioco | Chiusura/apertura lid associata a sleep/wake e scadenza della pausa preservata | LidClosePauseControllerTest, teardown e casi stop; deriva anche da #1589. Collaudo fisico successivo separato. |
| Ricerca cheat | Occorreva navigare manualmente molte cartelle/codici | Ricerca nomi/cartelle/opzioni, evidenziazione e salto preciso, query conservata | Adattamento #1677, esteso ai selector. Test search/index/navigation; il risultato non attiva il cheat. |
| Selettori di famiglie cheat | Centinaia di codici simili apparivano come voci indipendenti | Famiglie riconosciute dal database, scelta specie/livello/natura/starter, gruppi di esclusione | Test parser AR, riconoscimento, fixture XML, ViewModel e persistenza; profilo Sacred Gold Plus preserva il toggle L+R. Nessuna promessa su codici arbitrari. |
| UI leggibile con testo grande e tastiera | Titolo schiacciato, target 36dp, radio duplicata; in landscape IME poteva lasciare una pagina vuota | Target 48dp, un'azione radio, gerarchia adattiva, contenuto scorrevole, Fine senza conferma, barre leggibili nei due temi | 24 test UI nel totale JVM; immagini Android IT/EN, 100/130/200%, portrait/landscape e IME. TalkBack e gamepad fisici restano da provare. |

## 2. Correzioni app: difetto, prima e dopo

Le righe sono difetti o gruppi di difetti verificati, non un conteggio di issue
chiuse. I collegamenti alle prove della campagna originaria sono in
[REPORT](../2026-10-28-android-bugfixes/REPORT.md) e
[VALIDATION](../2026-10-28-android-bugfixes/VALIDATION.md); gli ultimi casi sono in
[STATUS](STATUS.md) e [VALIDATION finale](VALIDATION.md).

| Area | Prima osservato | Dopo verificato | Prova e confine |
| --- | --- | --- | --- |
| PCM microfono | Sei ingressi amplificati convertiti fuori intervallo; inversione di segno/overflow | Saturazione a s16 mantenendo il guadagno | 11 casi numerici, sanitizer e callback Android. Nessun cursore volume aggiunto. |
| Cursore acquisizione | Cambio sorgente/sessione poteva copiare una lunghezza negativa di −952 byte o campioni vecchi | Cursore posseduto dal buffer della sessione | Source switch, wrap, nuova sessione e silenzio; apertura hardware sostituita da adapter nei test di lifecycle. |
| Concorrenza microfono | Toggle, configurazione e teardown accedevano a stato/stream senza ownership coerente; soli flag atomici insufficienti | Controlli serializzati e flag atomici, nessun nuovo mutex nel callback realtime | Otto scenari TSan sulla translation unit di produzione, controlli negativi e ASan/UBSan. Driver reale da provare. |
| Output audio e resume | Apertura fallita poteva dereferenziare lo stream nullo; ripresa e mute avevano policy fragili | Controllo del risultato prima dell'accesso e ripristino ordinato delle policy | Test policy audio e controlli sorgente precedenti; non certifica cuffie/Bluetooth o qualità del resampler. |
| Scrittura save | Aperture troncanti e fallimenti poco visibili aumentavano il rischio di perdita | Preferenza per file esistente non troncato, errori segnalati e retry | SaveManager/file-mode test; precedente prova raw Continue→SAVE→cold restart. Provider di fallback possono ancora troncare: non è una sostituzione atomica universale. |
| NAND/BIOS/TMD | Input mancanti/corti o TMD di un altro titolo accettati; cleanup con categoria/ID invertiti | Validazione prima della mutazione e cleanup corretto | JNI reale per input invalidi, modello NAND per mutazioni, ASan sul TMD. Quota e HOME Menu reali non risolti. |
| Download TMD | Una sola lettura lasciava una coda a zero su risposta frammentata | Esattamente 520 byte oppure EOF | Cinque casi JVM e ART con frammenti, troncamento, vuoto e prefisso corretto. |
| Copia guidata BIOS | File invalidi accettati, errori sorgente nascosti; secondo setup poteva adottare NAND di 1024/32768 byte | Verifiche prima delle preferenze, mutex, file .part univoco, chiusura stream prima di rename; residuo non adottato | RED lifecycle/staging, 30 test BIOS e 4 SAF reali. Un provider bloccato deve ritornare prima del cleanup. |
| Import DAT/XML | Frammenti/offset/EOF mal gestiti; possibile loop e producer bloccato | Letture complete, validazione e propagazione della cancellazione | DAT sintetici validi, frammenti, troncamenti e flusso di 100 giochi; parser reale su JVM/ART. |
| Integrità database cheat | Parse o scrittura falliti potevano cancellare il database precedente | Sostituzione in una transazione Room e coda limitata | Confronto di tutte le righe dopo errori parser/SQLite/cancellazione, cheat personali preservati e import successivo. |
| Import con I/O bloccato | La cancellazione aspettava la lettura che ignorava interrupt; notifica/lavoro potevano restare attivi | Chiusura dello stream prima del join; segnale durante apertura provider | Pipe/PFD remoto, WorkManager reale, KEEP duplicato, notifica rimossa e nuovo KEEP riuscito su tre ambienti. |
| Scansione ROM | Eccezione lasciava stato SCANNING; refresh si sovrapponevano e liste già emesse cambiavano | Finalmente idle su errore, scansioni serializzate, snapshot stabili, cache preservata su query fallita | Sei regressioni iniziali, nove test scan/ordinamento verdi. Non è un benchmark del normale avvio. |
| Ordinamento | Veniva persistito il valore precedente | Persistito il nuovo ordine | Ricostruzione del ViewModel e verifica della preferenza. |
| ROM compresse e icone | Decodifiche 7z concorrenti/pesanti e caricamento icone dipendente dallo scan | Limite memoria e serializzazione, icone caricate separatamente, gestione archivio troppo grande | SevenZMemoryLimit/CompressedRomFileProcessor/RomIconProvider; nessuna garanzia contro tutti gli OOM. |
| Avvio da altre app | Alcuni URI non venivano associati alla ROM/permanenza permessi corretta | Matching Document-ID e permessi mantenuti nei casi supportati | Adattamento #1648 e regressioni del processor; raw file path può restare inaccessibile sotto scoped storage. |
| Riferimenti JNI DSiWare | Riferimenti locali accumulati per ogni titolo | Lifetime delimitato, oggetti della lista conservati | Harness JNI lifetime; correzione causale per #1678. |
| Display esterno | Scollegamento poteva lasciare un solo schermo; reconnect/rotazione perdeva layout | Fallback su display compatibili, layout conservato nelle transizioni verificate | Provider/generatore reali su JVM e Android. Non aggiunge toggle casting/mirroring né verifica OEM Thor. |
| Touch e controller | Puntatori stazionari, cancellazione/focus perso potevano lasciare coordinate o input vecchi | Centro contatti aggiornato, rilascio input e combinazioni su perdita focus | MotionEvent reali e test handler; nessun miglioramento input-to-photon misurato. |
| Lifecycle/FPS | Scope attivo troppo presto; vecchi vincoli del contatore; pausa lid persa allo stop | Scope per sessione, cancellazione della precedente, vincoli e pausa corretti | Tre RED precedenti e regressioni JVM conservate nel totale. |
| Fallback renderer | Fallita la factory accelerata, il core installava Software ma il frontend eseguiva un cast incompatibile | Tipo effettivo Software seguito da frame/screenshot; preferenza richiesta conservata | 7 casi UBSan RED, 10 GREEN host/NDK; shader GLES reale rifiutato, 2 fasi nuove e superficie visibile. |
| Filtri, lingue e CI | LCD/scanline disallineati; stringhe/formati mancanti; setup SDK falliva prima della compilazione | Filtri ancorati alla griglia, risorse complete, platform-tools e workflow aggiornati | Test sorgenti shader, lint 0 errori; CI storica attestata. Non è una nuova CI dell'ultimo commit locale. |

## 3. Cosa contiene la libreria core

L'ultima rifinitura non cambia il core. Queste modifiche erano già consolidate
in `fd758240` e devono essere descritte nella proposta lib, non attribuite al
fallback Android o a un nuovo intervento prestazionale.

| Modifica | Prima → dopo | Controllo/limite |
| --- | --- | --- |
| DS/DSi e registri SCFG | Inizializzazione/accessi impropri → regole coerenti con la modalità della cartuccia | Correzione sorgente consolidata e build/suite; non equivale a copertura di tutti i giochi DSi. |
| Power saving Wi-Fi | Il transceiver poteva spegnersi anche con risparmio automatico disabilitato → spegnimento condizionato alla modalità richiesta | Logica del core; nessuna percentuale di batteria misurata o sessione multiplayer completa certificata. |
| Literal JIT e invalidazione | Literal non tracciati e maschera impropria → folding soltanto con regione tracciata e maschera di scrittura effettiva | Regressioni sintetiche JIT, backend ARM64/x86_64; runtime Android distinto dalle prove Linux. |
| Teardown JIT | Lettura di ARM9 dopo distruzione → rilascio mapping mentre ARM9 esiste | UBSan rigoroso; corrisponde alla causa di upstream #2537. |
| Mapping DTCM | Scrittura RAM finiva nella DTCM; remap poteva asserire → finestre/offset/mapping simmetrici e invalidazione corretti | Controlli separati fast-memory/literal, sentinelle; finestre 4/8/16/32KiB, mirror e cambio base su Android 4/16KiB. |
| Lavoro renderer ridondante | Configurazione ripetuta e pass edge inattivo ancora preparato → evita lavoro immutato e shader/upload inutilizzati | Cambiamenti già presenti, nessun nuovo benchmark FPS/GPU o qualità 6x in questa campagna. |
| Override DNS opzionale | Resolver senza tabella frontend → tabella hostname→IPv4, vuota per default | Test pacchetti DNS; case, punto finale e tipo A. Le impostazioni WFC dell'app non dipendono da questa API. |

## 4. Issue e PR: cosa è chiuso davvero

Le nostre PR app [#1681](https://github.com/rafaelvcaetano/melonDS-android/pull/1681)
e [#1682](https://github.com/rafaelvcaetano/melonDS-android/pull/1682), lib
[#18](https://github.com/rafaelvcaetano/melonDS-android-lib/pull/18) e
[#19](https://github.com/rafaelvcaetano/melonDS-android-lib/pull/19) sono **chiuse
senza merge**. Sono proposte precedenti sostituite/consolidate, non bug risolti
upstream. Nessuna issue è stata chiusa su GitHub da questa rifinitura.

| Gruppo | Copertura nel fork | Stato/uso nella futura PR |
| --- | --- | --- |
| #1678 | Correzione precisa del lifetime JNI | Ancora open; candidata a `Fixes #1678` nella PR app. |
| #264 | Backup/ripristino impostazioni e layout richiesto | Ancora open; funzione implementata, test specifici; candidata alla chiusura dopo accettazione upstream. |
| #1253 | Copertura risorse cinese semplificato | Ancora open; non dichiarare revisione linguistica completa. |
| #1631 | DAT già presente upstream, ora parser/import rafforzati | Ancora open; non attribuirsi l'introduzione originale del formato. |
| #1679/#1285 | PCM, cursore e concorrenza migliorati | Parziale: manca slider volume e prova fisica della sensibilità. |
| #1651/#1099 | Errori input/TMD/cleanup corretti | Parziale: quota logica e HOME Menu reale restano aperti. |
| #1663/#1641/#1619 | Disconnect/reconnect/layout verificati | Parziale: hardware casting e nuove funzioni mirroring/toggle non coperte. |
| #1644/#1635/#1602 | Policy resume e mute migliorate | Parziale: Bluetooth e resampling/distorsione non certificati. |
| #1652/#1604 | Sync opzionale soltanto in avanti | Parziale: ogni sequenza lid/RTC del gioco richiede verifica. |
| #1519/#1378/#540 | Limiti 7z, errori scan e snapshot corretti | Parziale: librerie/provider reali problematici e normale durata startup da misurare. |
| #1141/#1424 | Correzioni puntatori e input | Restano aperti rapidità/latency specifiche dei giochi. |
| #579/#751 | Cause JIT sintetiche corrette | Nessuna chiusura dei crash specifici Super Mario256/AiRace senza la loro riproduzione. |
| #1634/#1177 | Configurazione WFC presente; nessun fix RA nuovo | Multiplayer/AP e achievement specifico ancora da riprodurre. |
| #1367/#961/#1626 | Precedenti storici utili alla ricerca | Già chiuse rispettivamente nel 2024, 2023 e maggio 2026; non sono nostre nuove chiusure. |
| core upstream #2537 | Causa teardown riprodotta e corretta nel fork Android | Ancora open; citare la corrispondenza tecnica, non chiuderla come se questa PR fosse nel repository upstream core. |

Tutti i numeri app della tabella hanno link e stato puntuale in
[GITHUB-STATUS.json](GITHUB-STATUS.json) e nella [matrice CSV](ISSUE-PR-COVERAGE.csv).
Il vecchio censimento di 246 issue resta consultabile: 15 COVERED, 56 PARTIAL,
109 INVESTIGATE, 4 NEW_CANDIDATE, 62 OUT_OF_SCOPE. COVERED significa presente nel
codice; include funzioni upstream conservate e non significa 15 nuovi bug nostri.

| PR di terzi | Stato integrazione nel fork | Prova/decisione |
| --- | --- | --- |
| #1677 — takanuva15 | Adattata ed estesa | Ricerca+selector, query/navigazione, UI; credito preservato. |
| #1606/#1608 — WailAbou | Adattate | Combo e hold; test input/policy. |
| #1625 — dysprosium | Adattata | Mute fast-forward; non sostituisce una soluzione di fedeltà audio. |
| #1648 — N3MI-DG | Adattata | URI esterni e regressioni processor. |
| #1589 — DLCSharp | Adattata | Sleep/lid; regressioni pausa/stop. |
| #1666 — corigne | Soltanto parti mirate | Recovery completo dopo process death non dichiarato integrato/verificato. |
| #1657/#1664/#1524 | Confrontate; correzioni mirate/locali, non intere PR importate | Pausa/render, RTC e limite 7z descritti separatamente. |
| #1676; lib #17 | Non integrate | Rate matching/pitch e buffer più grande richiedono misure qualità/latency. |
| #1035; lib #1/#3 | Considerate, nessuna nuova importazione | Documentazione BIOS, enable-tag e percorsi firmware sono ambiti distinti. |

Le PR di terzi sopra risultano ancora open upstream nella verifica mirata.
Non le abbiamo chiuse né fuse upstream. Il riuso nel fork conserva autore e
provenienza e non autorizza a dichiarare risolta tutta la loro motivazione.

## 5. Controlli conclusi e confrontabilità

| Livello | Risultato della build funzionale finale | Cosa non dimostra |
| --- | --- | --- |
| JVM/Robolectric | 267 pass, 0 errori/fallimenti/skip; inclusi 30 BIOS e 24 UI | Driver fisici o prestazioni Thor. |
| ART/JNI | 52 pass + 3 skip opt-in per ambiente: API33/4KiB e API35/4KiB/16KiB | Non sommare gli skip ai pass né i sottoinsiemi al totale JVM. |
| WorkManager reale | 1 caso separato per ciascuno dei tre ambienti | Non estende la cancellazione a ogni possibile provider. |
| Native Android | 6 eseguibili passati sui tre ambienti; renderer 10 scenari | Adapter non equivalenti a driver hardware. |
| Host sanitizer | Mic 8 scenari TSan; renderer 10 ASan/UBSan; precedenti policy/storage/JIT | Nessun risultato del frontend Mac e nessuna misura energetica. |
| GLES reale nell'app | Software controllo, Compute e OpenGL: 3 invocazioni API33/GLES3.1 | Verifica fallimento/fallback, non qualità o velocità del renderer accelerato. |
| UI nativa | Screenshot confrontati, tastiera reale, Fine e contrasto corretti | TalkBack pronunciato e gamepad non provati. |
| Build/lint | APK ARM64 e test APK; 0 errori lint, 474 warning | Warning non azzerati; CI del commit finale locale ancora da eseguire dopo eventuale push autorizzato. |
| Revisione | Diff immutabile + delta finali: nessun rilievo aperto | Restano i limiti fisici e di protocollo dichiarati. |

APK SHA256 `72c3e4363f74da95e9e02df44fa3cba523ddf51ca2216e6992d220682926ec1b`;
frontend BuildID `f108ca62588fed32ae60805bed94bad54581d9cd`.
L'APK Thor acquisita e usata attualmente dalla campagna ROM è un'altra build,
`.nightly.perf`, senza commit sorgente attestato. Le sue prove di gioco e i suoi
problemi non vengono attribuiti automaticamente a questa APK `.dev`.

Non è stato raggiunto un calo del numero totale di righe. Nel solo commit finale
funzionale: codice di produzione +660/−283, **saldo +377 righe**; test e strumenti
+4370/−22; risorse +61/−13; documentazione/configurazione +900/−5. Sono misure di
diff, non memoria o istruzioni eseguite. Sono eliminati alcuni passaggi ridondanti
(es. seconda apertura del file importato), ma la protezione di lifecycle/copie
richiede nuovo codice. Non rivendichiamo riduzioni RAM, CPU, GPU, joule o secondi
di startup prive di A/B confrontabile.

## 6. Bozze delle due PR

Le bozze di questa revisione restano locali. Le panoramiche correnti di
[app](../../../README.md#whats-new) e
[lib](https://github.com/Umberto-DEV/melonDS-android-lib#whats-new) riassumono
funzioni, correzioni e crediti. Titolo/problema iniziale, comportamento
prima/dopo, provenienza, verifiche con build esatta e limiti devono stare nel
corpo; il dettaglio per issue resta in questa matrice, senza una lista di chiusure
indiscriminate. Solo la correlazione JNI precisa usa il riferimento automatico
`Fixes`; gli altri casi sono descritti come funzione, parziale o follow-up.

La proposta lib contiene solo il codice condiviso; la proposta app contiene UI,
SAF/Room/Oboe/JNI, workflow e harness. Partenza dai nostri `main`, target upstream
`master`, senza nuove branch. Prima lib, poi app collegata all'effettiva revisione
core accettata: se cambia SHA, aggiornare gitlink/URL e ripetere i controlli
pertinenti. Inserire gli URL reciproci soltanto dopo l'autorizzazione a pubblicare.

Prima della pubblicazione: aggiornare confronto col target upstream, verificare
CI della revisione esatta e link reciproci, decidere la gestione dei messaggi
storici e conservare i crediti umani. Il test fisico resta esplicito se non ancora
eseguito; non va trasformato in un risultato verde. Nessuna pubblicazione da
questo riepilogo.

## 7. Cosa manca

- Installazione coordinata e accettazione dell'ultima build sulla Thor: microfono,
  controller, sleep/audio, doppio display e storage reali; copie dei save protette.
- Misure avvio normale con libreria utilizzabile/scan finito distinti, memoria per
  processo, CPU/GPU/energia e sensori identificati. L'AVD non replica Adreno 740.
- Ottimizzazione upscaling 6x: pipeline OpenGL ES 3.2/GLSL, non OpenCL; sprite 2D non
  diventano automaticamente 3D. Il piano visibilità GPU è pronto, non applicato;
  texture reuse/span upload e port 2D upstream richiedono esperimenti propri.
- Full Activity rotation Android durante copia NAND su provider esterno lento,
  TalkBack manuale, navigazione fisica, provider non cooperativi e atomicità SAF
  rispetto a scrittori esterni. Non promettere autenticità BIOS/NAND.
- I problemi specifici di giochi/RA/multiplayer, quota DSi e richieste di nuove
  funzioni elencati sopra; nessun nuovo audit generico duplicato.

## 8. Testo dei commit e conservazione

Il commit funzionale più recente e le bozze non contengono firme automatiche o
collegamenti di sessione. La convenzione è registrata nelle istruzioni del repo
per app e lib. Nei due documenti storici che contenevano questi riferimenti,
i testi sono stati resi neutrali; i crediti ai contributori restano.

L'audit della storia specifica dei fork ha trovato **89 commit app e 2 lib** con
riferimenti a strumenti/assistenti, tutti già antenati dei rispettivi riferimenti
remoti pubblicati. Non sono modificabili con una normale correzione di file:
cambierebbero SHA e discendenti, incluso il gitlink core. Il nuovo standard non
rimuove quei messaggi dal passato. Nessuna riscrittura o force-push è stata
eseguita; occorre una decisione esplicita del maintainer prima di procedere.

Coordinamento finale effettuato con la chat V1.3.0. Verificati sul filesystem
**255 output ROM obsoleti già assenti**, corrispondenti a 25,91 GiB logici del suo
manifesto precedente; non è spazio appena liberato da questa chat. Le quattro
ROM canoniche correnti/fallback EN/IT esistono. La nostra campagna aveva già
rimosso circa 1,94 GiB logici di intermedi e l'AVD vuoto, conservando app/test APK,
simboli compressi e piccole prove; circa 63 GiB liberi al termine di quella pulizia.

Al controllo del 28 ottobre V1.3.0 usava ancora emulator-5580 e fixture Android.
Il passaggio finale del **29 ottobre** attesta la conclusione della campagna ROM
e lo spegnimento verificato del banco. Le impronte delle due ROM v3 correnti e
del profilo Software 1x sono state ricontrollate. Queste prove usano l'APK
acquisita `.nightly.perf`: non si trasferiscono alla nostra `.dev` o a Compute 6x.

La chat centrale «Auditare l'uso di spazio su disco» mantiene la responsabilità
della pulizia condivisa ed è attiva; al ricevimento del manifesto finale non era
ancora disponibile un suo esito conclusivo. Non sono state duplicate cancellazioni.
ROM sorgenti, BIOS, save originali, fixture, release pubblicate e prove uniche
restano protetti. I manifesti privati conservano percorsi esatti, impronte,
ruoli corrente/precedente e responsabilità; "superato" non significa di per sé
eliminabile.
