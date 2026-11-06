# Correzioni verificate per melonDS Android

Mandato: correggere difetti riproducibili, mantenere un solo `main` e conservare descrizioni/attribuzioni per future PR. **Pubblicazione delle nuove PR ancora bloccata dall’utente.** I push sui fork servono alla CI.

La base di questa campagna è app `05c38d61`, versione dichiarata **2.1.4**, con core **1.1**, gitlink `fd7582400bb7e35fe0d36dbb0c89b6766c1a8f2e`. Il core non è stato cambiato in questa campagna. La versione Sacred Gold Plus 1.2.5 identifica invece la ROM usata nella prova di integrazione: non è la versione dell’emulatore.

## Cosa abbiamo corretto adesso

Le otto righe sono **gruppi di difetti del codice**, non otto issue GitHub chiuse. Ogni correzione deriva da una regressione che falliva prima dell’intervento. Sono inclusi i tre rilievi importanti della revisione indipendente, anch’essi riprodotti prima della correzione.

| ID | Difetto e correzione | Prova concreta | Relazione con GitHub |
|---|---|---|---|
| A1 | Microfono amplificato: conversione fuori intervallo invertiva il segno dei campioni. Saturazione prima della conversione a `s16`, guadagno invariato. | 11 ingressi PCM con risultati numerici indipendenti; sei fallivano prima, UBSan rilevava overflow. Callback reale con Oboe su Android. | [#1679](https://github.com/rafaelvcaetano/melonDS-android/issues/1679), [#1285](https://github.com/rafaelvcaetano/melonDS-android/issues/1285) restano parziali: nessun cursore volume o certificazione della sensibilità fisica. |
| A2 | Passaggio soffio→microfono e nuova sessione riutilizzavano un cursore appartenente a un altro buffer: copia negativa/crash o campioni vecchi. Cursore posseduto dal buffer di acquisizione. | Copia negativa di −952 byte prima della modifica; controlli sessione, wrap, silenzio e disattivazione. Produzione C++ con apertura stream sostituita dal fixture. | Difetto locale scoperto nel controllo audio; non viene attribuito automaticamente alle segnalazioni Bluetooth/resume. |
| N1 | NAND mancante segnalata come successo, BIOS corto letto senza verifica, TMD corto o di un altro titolo accettato, pulizia import fallito con categoria/ID invertiti. Validazione prima della mutazione e argomenti corretti. | JNI reale nell’APK per NAND/BIOS invalidi; modello JNI/NAND con file temporanei per integrità, duplicati, errore parziale e successo. ASan riproduceva lettura oltre il TMD. | Pertinente a [#1651](https://github.com/rafaelvcaetano/melonDS-android/issues/1651), [#1099](https://github.com/rafaelvcaetano/melonDS-android/issues/1099); **quota logica e boot HOME da NAND reale restano aperti**. |
| N2 | Download TMD eseguito con una sola lettura: risposta frammentata/troncata diventava metadato con coda a zero. Lettura esatta di 520 byte o EOF. | Stessi cinque casi su JVM e ART: completo, frammenti di17 byte,15 byte, vuoto, dati oltre il prefisso; verifica contenuto e chiusura stream. | Nuovo difetto del percorso DSiWare, senza promozione automatica delle due issue di quota. |
| D1 | Dopo scollegamento del display esterno mancava uno schermo DS. Il fallback ora richiede display compatibili; riconnessione con nuovo ID e rotazione degli inset mantiene il layout personalizzato. | Flusso pubblico del provider e generatore layout reali: disconnect, layout con schermo intenzionalmente nascosto, reconnect, reconnect+rotazione. JVM e Android. | Area di [#1663](https://github.com/rafaelvcaetano/melonDS-android/issues/1663), [#1641](https://github.com/rafaelvcaetano/melonDS-android/issues/1641), [#1619](https://github.com/rafaelvcaetano/melonDS-android/issues/1619); non implementa toggle casting/mirroring né prova la Thor. |
| I1 | Dito fermo aggiunto o sollevato lasciava coordinate vecchie fino al MOVE seguente. Aggiornato il centro dei contatti, escludendo soltanto il dito sollevato. | MotionEvent reali su JVM/Android:2/3 dita, indice0/1, un solo press/release, cancellazione. | Difetto locale; non dichiara risolti [#1141](https://github.com/rafaelvcaetano/melonDS-android/issues/1141), [#1424](https://github.com/rafaelvcaetano/melonDS-android/issues/1424) o [lib#8](https://github.com/rafaelvcaetano/melonDS-android-lib/issues/8). |
| C1 | DAT frammentato rifiutato; EOF durante skip generava ciclo infinito; progress arretrava a EOF; offset invalidi/blocchi assenti erano accettati come giochi vuoti; annullamento di un consumer poteva lasciare il producer bloccato. Letture complete, validazione e cancellazione dell’intero parser. | DAT sintetico valido di356 byte, frammenti7, troncamenti/offset, byte/code attesi; callback cancellato e flusso reale a100 giochi. JVM e ART. | Rafforza la funzione già presente di [#1631](https://github.com/rafaelvcaetano/melonDS-android/issues/1631); non dimostra correzione dei crash causati da singoli codici nei giochi. |
| C2 | Import XML/DAT non valido o scrittura fallita cancellava il database precedente prima di terminare. Ora la sostituzione è atomica in Room; flusso limitato a pochi giochi, parser fuori dall’executor della transazione. | Worker, parser, ContentResolver e SQLite reali: validi XML/DAT, file troncati, header mancante, errore SQLite, cancellazione producer/consumer. Confronto di tutte le righe e preservazione dei cheat personalizzati. | Nuovo difetto riprodotto localmente. [#1367](https://github.com/rafaelvcaetano/melonDS-android/issues/1367) è un precedente storico già chiuso/duplicato di#1305: non rivendichiamo quel crash come risolto da questa patch. |

Non sono stati misurati aumenti FPS, riduzioni dell’input lag o aumenti di risoluzione. La semplificazione elimina una continuazione manuale superflua e rende esplicita l’atomicità; non è presentata come benchmark prestazionale.

## Già presente prima di questa campagna

L’inventario precedente contiene15 voci “coperte nel codice”, con limiti espliciti. Non sono15 nuove correzioni nostre:

- Nostri interventi precedenti: riferimenti JNI DSiWare [#1678](https://github.com/rafaelvcaetano/melonDS-android/issues/1678), backup impostazioni/layout [#264](https://github.com/rafaelvcaetano/melonDS-android/issues/264), copertura delle traduzioni in cinese semplificato [#1253](https://github.com/rafaelvcaetano/melonDS-android/issues/1253). La qualità linguistica/dispositivo non segue automaticamente dalla copertura stringhe.
- Comportamento upstream conservato e nostre regressioni aggiuntive: pulsanti ABXY multi-pointer #1547/#1446/#1247/#1223/#578/#548. I nuovi test sul touchscreen DS sono distinti dai pulsanti virtuali.
- Funzioni già upstream: intent della stessa ROM #1659, posizione rewind #1655, import DAT #1631, più cartelle ROM #1575, categoria Android game #1359. Nessuna nuova attribuzione di queste implementazioni.
- [lib#2](https://github.com/rafaelvcaetano/melonDS-android-lib/issues/2): aggiornamento core1.1 presente; C-Gear non certificato.
- JIT teardown e alias DTCM erano già corretti prima di questa campagna e nuovamente esercitati con programmi sintetici Android4/16K. [Ricerca e prove precedenti](../2026-10-28-main/KNOWN-JIT-ISSUES.md). Il teardown corrisponde a [core#2537](https://github.com/melonDS-emu/melonDS/issues/2537); nessuna nuova correzione del frontend Mac.

## Stato delle13 categorie

L’[inventario completo precedente](../2026-10-28-main/ISSUE-CATEGORIES.md) contiene246 issue aperte,242 app e4 lib. L’[aggiornamento per ogni issue](ISSUE-STATUS.csv) conserva numero, fonte e motivazione; un test del componente non viene convertito in una chiusura dell’intera segnalazione.

| Categoria | Issue | Risultato applicabile | Cosa serve ancora |
|---|---:|---|---|
| Audio/microfono |12| A1/A2; precedenti policy resume/fast-forward | Thor/microfono fisico, livelli controllati, codec Bluetooth e registrazioni; gain UI da progettare. |
| Grafica/display |31| D1; precedenti correzioni LCD/scanline e renderer | HDMI/casting/Thor, driver reali; nessuna promessa su scaling/OpenGL di ogni gioco. |
| Touch/controller |20| I1, ABXY e rilascio input già coperti | Offset Thor e tempistiche sub-frame in giochi specifici; misura input-to-photon. |
| Save/stati/rewind |13| Scrittura preservante e impostazioni precedenti; controllo di integrazione con copia raw | Riprodurre exit/kill/SAF specifici, stato incompatibile, rimappature e rollback con fixture. |
| ROM/SAF/archivi |14| Limite memoria7z, lock scansione, icone e intent già presenti | Libreria problematica/SD/provider e heap/log della segnalazione; nessun OOM generico dichiarato risolto. |
| Avvio/BIOS/DSi |43| N1/N2; precedente JNI#1678 | NAND/BIOS autorizzati, titoli e quota reale; HOME Menu e firmware prima/dopo. |
| JIT/prestazioni/compatibilità |37| JIT sintetico su Android4/16K; core invariato | ROM/save/scena precisa per AiRace, Super Mario256 e altri hack; profili Android rappresentativi. |
| Rete/multiplayer |29| Configurazione WFC/DNS e test pacchetti già presenti | #1634 ricerca AP richiede ROM/scena; sessioni WFC e trade richiedono peer/server/firmware. DNS non equivale a multiplayer corretto. |
| Cheat |4| C1/C2; ricerca e selector già coperti dalle regressioni | Codice/Game-ID e scena per crash provocati da cheat. |
| RetroAchievements |6| Nessuna nuova causa dimostrata | ROM/hash supportato, achievement preciso, save/account e tracce trigger/invio; #1177 offre una pista concreta. |
| Ciclo vita Android |20| Scope sessione, rilascio input/audio, lid/pause già presenti | Process death/ChromeOS e manual lid RTC; sincronizzazione soltanto in avanti resta intenzionale. |
| Interfaccia/lingue |13| Copertura traduzioni/lint e ricerca cheat precedenti | Resa/qualità linguistica su dispositivo; temi/artwork/editor numerico sono feature. |
| Distribuzione/altro |4| CI fork e build di verifica | F-Droid e nuove periferiche richiedono lavoro separato; nessuna release pubblicata. |

Le richieste ampie non sono classificate come bug “impossibili”: mancano una riproduzione, un progetto di funzione o un ambiente appropriato. Le correzioni dimostrate qui sono separabili e verificabili; le altre non vengono dichiarate verdi per raggiungere un numero.

## PR, provenienza e prossima pubblicazione

Le descrizioni originali rimangono intatte: [app#1682](../2026-10-28-main/app-pr.md), [core#19](../2026-10-28-main/core-pr.md), relativi JSON e commenti nella stessa cartella. Le due proposte furono chiuse durante il consolidamento. Le bozze aggiornate rimangono locali; le panoramiche correnti di [app](../../../README.md#whats-new) e [core](https://github.com/Umberto-DEV/melonDS-android-lib#whats-new) riassumono funzioni, correzioni e crediti.

La futura sorgente è il `main` del nostro fork; il target upstream è `master`. Restano i contributi/crediti già conservati: takanuva15#1677, WailAbou#1606/#1608, dysprosium#1625, N3MI-DG#1648, DLCSharp#1589, corigne per parti di#1666. Questa campagna aggiunge correzioni originali; non incorpora nuove patch di terzi senza attribuzione.

PR riesaminate come piste, senza applicazione indiscriminata: #1676(audio rate/pitch),#1524(7z),#1657(idle/render),#1664(RTC),#1666(recovery); lib#17(buffer audio),#1(enable cheat),#3(firmware paths). Aumentare un buffer o cambiare rate matching senza una misura Android non costituisce una correzione verificata. I precedenti interventi equivalenti e le parti già adattate restano distinguibili nelle bozze.

[Verifica, comandi e limiti](VALIDATION.md). Il successivo audit coordinato e l’autorizzazione esplicita dell’utente restano necessari per pubblicare nuove PR.
