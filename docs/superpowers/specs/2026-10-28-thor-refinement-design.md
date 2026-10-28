# melonDS Android: affidabilità, avvio e interfaccia sulla Thor

## Mandato

Migliorare melonDS Android mantenendo funzioni, dati e identità visiva attuali.
Ridurre lavoro inutile, allocazioni e complessità quando una misura o una
riproduzione ne dimostra il vantaggio. Il numero di righe non è una metrica di
prestazione: non eliminare controlli, gestione errori o compatibilità per accorciare
il codice. Ogni correzione parte da un caso fallente, conserva un controllo e
documenta il risultato dopo la patch. Nessun guadagno numerico presunto.

L'utente ha autorizzato analisi e prosecuzione autonoma, richiesto Superpowers e
Impeccable e autorizzato una chat dedicata all'upscaling. Le scelte operative qui
sotto sono del coordinatore, correggibili dall'utente; non sono nuove preferenze
attribuite all'utente. Nessuna ulteriore approvazione del piano è necessaria per il
lavoro locale già richiesto. Pubblicare PR richiede ancora autorizzazione esplicita.

## Base e coordinamento

- App `main` iniziale `c543c3675ef28d99b77749d391d135397d8adb8c`, ultimo codice
  verificato `19071123`; core `main`/gitlink `fd7582400bb7e35fe0d36dbb0c89b6766c1a8f2e`.
- Preservare i file non tracciati preesistenti: piano cheat del 24 ottobre e
  `docs/ventuno/audit-2026-10-24/`.
- [Campagna precedente](../../maintenance/2026-10-28-android-bugfixes/REPORT.md):
  246 issue in 13 categorie; otto gruppi di difetti corretti. I 224 test JVM e
  39 test ART/JNI per ambiente sono risultati storici, non di questa fase.
- Daybreak ha concluso il primo ciclo: due riproduzioni private su cancellazione
  import bloccato e flag microfono concorrente. Usare queste prove, senza rifare
  un audit a tappeto o attribuire loro crash specifici non osservati.
- La chat Sacred Gold Plus ha concluso l'acquisizione Thor, ripristinato le
  preferenze e verificato 14 originali. Non ripetere acquisizione, benchmark o ADB
  mentre un altro coordinatore usa il dispositivo.
- Chat separata **melonDS Android: qualità e costo dell’upscaling**: ricerca e
  proposte private in sola lettura; nessuna build o modifica concorrente del core.
- La pulizia disco è centralizzata in un'altra chat. Durante la pulizia evitare
  build/test e nuove copie grandi; conservare evidenze uniche e due APK utili
  secondo il registro di conservazione, non cancellare autonomamente.

## Approccio scelto

Tre alternative considerate: riscrittura generale; sola rifinitura visiva;
interventi piccoli guidati da riproduzioni. Scelta del coordinatore: la terza,
perché rende attribuibili gli effetti e protegge i miglioramenti già collaudati.
Nessuna migrazione Compose Material 3, nuova libreria grafica o revisione del
formato save fa parte della rifinitura. Si conserva Material/AppCompat attuale;
le indicazioni generiche della skill non prevalgono sul design system richiesto.

Ordine: due difetti Daybreak → scansione/avvio → affidabilità e chiarezza BIOS →
ricerca/selettori cheat → esperimento rendering selezionato dalle prove. I temi
restano progetti verificabili separatamente; non accumulare una grande patch.

## Progetti e accettazione

### A. Operazioni interrompibili e microfono

Import: cancellare dopo l'inizio di una vera lettura bloccante deve liberare
parser e transazione, preservare tutte le righe precedenti e permettere un
secondo import. Il test sincronizza l'ingresso nella lettura; niente sleep come
unico oracolo. Un `CancellationSignal` sull'apertura non prova l'interruzione
delle successive read; `close()` e interrupt vanno verificati con provider Android
reali/pipe. Il Worker va provato anche nello scheduling WorkManager, non solo
chiamando un metodo privato. Nessun rollback dichiarato da un mock.

Microfono: definire ownership di flag, sorgente, stream e start/stop. TSan deve
fallire prima sul codice di produzione e passare dopo; controllare sorgenti
silenzio, rumore e Oboe con teardown/configurazione. Nessun mutex bloccante nel
callback realtime. La sola sostituzione del flag con atomic non certifica lo
stream hardware. Permessi, pausa/resume e reset su Android sono un gate separato.

### B. Libreria ROM e rotellina

Endpoint separati: prima UI, ROM in cache utilizzabili, fine scansione, lancio
ROM. Non nascondere un lavoro ancora attivo né disabilitare automaticamente la
scoperta di nuove ROM per ottenere una misura migliore.

Riprodurre prima: provider lento, errore, cartella rimossa/permesso revocato,
richieste refresh concorrenti, archivio invalido e cache già popolata. Verificare
che lo stato torni inattivo su errore e che un refresh successivo funzioni; lista
precedente utilizzabile, niente emissioni di liste condivise mutate in seguito.
`limitedParallelism(1)` non equivale a un mutex tra coroutine sospendibili.

Misurare query/open/byte letti e scritture cache con lo stesso albero sintetico.
Un'ottimizzazione della cache deve ancora rilevare aggiunta, rimozione e modifica
di ROM, preservando dati di utilizzo. Verificare il bug della Thor su APK nota:
questa diagnosi sorgente non prova che il sintomo sul vecchio APK abbia la stessa
causa. Il salvataggio dell'ordine di elenco è un caso locale separato.

### C. Configurazione BIOS

Un percorso nativo: spiegazione breve → scelta cartella → lavoro con stato
comprensibile → esiti separati DS/DSi. Distinguere copiato, già presente,
mancante, ambiguo ed errore di lettura/scrittura. Non chiamare "mancante" un file
trovato la cui copia è fallita. Non dichiarare completa la cartella DSi senza
NAND; validare anche i file già presenti prima di cambiare una preferenza valida.
Il contenuto NAND non viene autenticato da nome o dimensione.

Nessuna sovrascrittura/cancellazione di sorgenti o destinazioni preesistenti.
Rimozione della sola copia parziale creata dall'operazione, con fallimento della
pulizia riportato; nessuna promessa di atomicità SAF universale. Cancellazione
e ricreazione Activity vanno provate su copia lenta, non dedotte da `withContext`.
Riutilizzare l'ownership I/O di A soltanto se i contratti sono realmente uguali.

### D. Interfaccia cheat

Conservare ricerca su cartelle/codici/opzioni generate, evidenziazione, query
durante navigazione e apertura del risultato senza attivazione automatica.
Usare gli helper colore melonDS anche nel selettore, righe selezionabili con
semantica radio unica e area minima 48 dp, label lunghe e pulsanti sempre
raggiungibili con tastiera, font ingrandito e gamepad.

Controllare chiaro/scuro, IT/EN, telefono compatto e layout landscape Thor,
font 1.0/1.3/2.0, tastiera aperta, TalkBack e navigazione controller. Screenshot
da APK Android corrispondente al commit; immagini HTML non sono verifica UI.
Lo screenshot Thor dei cheat attivi documenta il vecchio tema, non la nuova
ricerca. La cattura denominata `romlist-return.png` mostra il pixel refresh
di sistema: esclusa come prova della libreria.

### E. Rendering e risorse

Distinguere renderer software, OpenGL e compute, risoluzione interna 3D,
composizione 2D e filtro finale. Accertare API effettiva prima di parlare di
OpenCL. Confronti a qualità invariata prima di confronti con qualità ridotta;
1x contro 6x è una diagnosi del costo, non un miglioramento equivalente.

Scena e azioni identiche, frame guest distinto dalle presentazioni dei display,
immagini alla stessa progressione di gioco e non solo allo stesso tempo wall.
Nessuna texture più dettagliata promessa da un filtro. Prima opportunità:
eliminare solo lavoro del renderer dimostrato inutile, con equivalenza immagini.

## Laboratorio e misure

Il pacchetto Thor esiste e va referenziato senza duplicarlo. Contiene APK esatta,
firma/Build ID, ROM/raw/BIOS, export preferenze, mapping, layout, cinque cheat
verificati, log e misure. L'APK PROFILING non è debuggable né profileable; il
nome non prova i flag né la revisione sorgente. Mac AVD: Android 13 ARM64/4KiB
per la replica; Android 15 4/16KiB per le regressioni esistenti. Riconcedere SAF
dalla UI, adattare esplicitamente il display, usare copie raw e cold Continue.

| Domanda | Misura valida | Limite |
| --- | --- | --- |
| Avvio più rapido? | cold/warm separati; prima UI, cache pronta, scan end | 10 prove pilota appaiate; aumentare se variabilità alta; non inventare p95 affidabile da pochi run |
| Meno CPU? | CPU-s per lavoro uguale, thread/scheduler validati | percentuale un core non è percentuale del SoC |
| Meno RAM? | PSS/RSS, heap Java/native, allocazioni e picchi campionati | PSS non conta byte scritti in RAM né banda DRAM |
| Meno GPU? | timestamp/query GPU supportate e busy counter qualificato | frequenza istantanea non è tempo GPU o consumo |
| Meno energia? | joule su finestra uguale con contatore disponibile | rail non garantite sulla Thor; batteria in carica inadatta all'attribuzione |
| Meno calore? | sensori identificati, tempo, stato termico, ambiente/fan fissi | temperatura batteria non è temperatura CPU; Thermal HAL non pronta è dato mancante |
| Scritture corrette? | hash, intervalli consentiti, fault injection, raw cold Continue | non confondere RAM guest, allocazioni host e scritture su storage |

Memorizzare unità, PID/starttime, versione strumenti, configurazione, condizione
alimentazione, warmup, ordine A/B alternato e corse escluse con ragione. Separare
profiling pesante da misure leggere. Energia e driver Thor richiedono hardware;
un AVD sul Mac consente regressioni Android, non un coefficiente universale
Mac→Thor. Le tracce storiche non misurano una nuova build.

## Uso delle skill e Daybreak

Superpowers: systematic-debugging per causa/controprova; writing-plans e poi
executing-plans; TDD sui difetti; revisione e verifica prima di dichiarare esiti.
Impeccable: audit Android, documentazione dei token, harden/clarify/adapt e polish
mirati; leggere craft-floor prima di modificare UI e controllare l'APK renderizzata.
OpenAI Docs: verificare il ruolo di Daybreak nelle fonti ufficiali.

Daybreak è utile per ownership, parser e input non fidati, race, limiti memoria,
rollback e validazione delle patch. Non è una certificazione di sicurezza né un
profiler grafico. Prima consumare i due rilievi esistenti. Il ciclo successivo
deve coprire al massimo due confini nuovi, con SHA, chiamanti, precondizioni,
test fallente, controllo negativo, impatto e limite; niente risultati duplicati,
commit concorrenti o nuove scansioni generali senza una domanda precisa.

## Fonti e tracciabilità

- [Inventario originale](../../maintenance/2026-10-28-main/ISSUE-CATEGORIES.md),
  [stato per issue](../../maintenance/2026-10-28-android-bugfixes/ISSUE-STATUS.csv).
- Ricerca GitHub mirata 28 ottobre: [rescan #961](https://github.com/rafaelvcaetano/melonDS-android/issues/961),
  [7z #1626](https://github.com/rafaelvcaetano/melonDS-android/issues/1626),
  [scan/crash #540](https://github.com/rafaelvcaetano/melonDS-android/issues/540).
  Sono piste, non chiusure rivendicate; la ricerca mirata non è un nuovo censimento completo.
- [Avvio Android](https://developer.android.com/topic/performance/issues/launch-time),
  [Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview),
  [metriche](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics),
  [memoria](https://developer.android.com/topic/performance/memory-management),
  [Perfetto power](https://perfetto.dev/docs/data-sources/battery-counters),
  [accessibilità](https://developer.android.com/guide/topics/ui/accessibility/apps).
- [Daybreak: evidenze, ambito e verifica](https://developers.openai.com/blog/scaling-cyber-defenders-with-daybreak).
