# Rifinitura Android e laboratorio Thor — 28 ottobre 2026

Le correzioni di questa campagna sono verificate localmente su `main`, sopra app `c543c367`;
il core resta `fd758240`. Nessuna installazione sulla Thor, push o pubblicazione
PR. Le verifiche sul Mac usano Android virtuale ARM64: non certificano consumi,
GPU Adreno, controller, doppio display o driver fisici della Thor.

[Riepilogo completo, prima/dopo e bozze PR](INTEGRATION-REPORT.md).

[Mandato](../../superpowers/specs/2026-10-28-thor-refinement-design.md) ·
[Piano e gate residui](../../superpowers/plans/2026-10-28-thor-refinement.md) ·
[Revisioni indipendenti](INDEPENDENT-REVIEW.md).

## Censimento riutilizzato

Il censimento esiste già: [246 issue, 13 categorie](../2026-10-28-main/ISSUE-CATEGORIES.md),
242 app e 4 lib. Lo [stato per issue](../2026-10-28-android-bugfixes/ISSUE-STATUS.csv)
distingue correzioni complete, parziali e problemi aperti. Questa campagna non
chiude intere categorie a partire da una regressione di componente.

La ricerca mirata ha ritrovato precedenti di scansione (#961, #540) e archivi
7z (#1626). PR #1648 era già adattata; #1682 è la proposta storica. Nessuna nuova
patch di terzi importata. Una query senza risultati sugli aggiornamenti dal
24 ottobre non equivale a un censimento esaustivo aggiornato di tutto GitHub.

Gli assi aggiuntivi utili sono cancellazione/ownership, lifecycle con SAF,
osservabilità dell'avvio, accessibilità e identità delle build. Non sono nuove
categorie create per aumentare artificialmente il numero di problemi.

## Correzioni e prove prima/dopo

| Area | Difetto riprodotto | Correzione e verifica |
| --- | --- | --- |
| Import cheat, DB-ANDROID-02 | Lettura bloccata che ignora interrupt; cancellazione non chiude il flusso | Ownership del flusso e CancellationSignal per l'apertura; rollback Room esatto e nuovo import. Pipe Android e provider remoto passano; WorkManager reale verifica KEEP, stop, notifica e retry. |
| Microfono, DB-ANDROID-01 | Race tra toggle, cambio sorgente, avvio/stop e teardown; i soli flag atomici non bastano | Controlli serializzati e flag atomici, senza nuovo mutex nel callback realtime. Otto scenari TSan sulla translation unit di produzione, controlli negativi, ASan/UBSan e regressioni NDK. Driver fisico ancora da provare. |
| Libreria ROM | Eccezione lascia lo spinner attivo; refresh sovrapposti e snapshot mutabili | Scansioni serializzate, ritorno allo stato idle in finally, snapshot stabili e cache conservata su query fallita. Sei fallimenti reali, poi nove test scan/ordinamento verdi. |
| Ordinamento | La preferenza salva il vecchio ordine | Persistenza del nuovo ordine, verificata ricostruendo il ViewModel. |
| Setup BIOS | Risultati incompleti/invalidi accettati; errori di lettura nascosti; copia parziale adottata da un secondo setup | Verifica effettiva DS/DSi/NAND prima delle preferenze, errori e Retry espliciti, copie serializzate con file temporanei univoci e rename dopo chiusura degli stream. Trenta test JVM, più quattro test Binder/SAF/PFD reali. |
| Ricerca/selettore cheat | Riga troppo piccola, radio con azioni duplicate, titolo schiacciato e dialogo vuoto con IME | Helper del tema esistente, target 48dp, singola semantica radio, contenuto scorrevole e un solo proprietario degli inset. Ventiquattro test UI; screenshot Android chiaro/scuro, testo grande e IME. |
| Fallback grafico | Factory Compute/OpenGL fallita installa Software nel core, ma il frontend esegue un cast del tipo accelerato | Il frontend registra il tipo effettivo e non applica impostazioni incompatibili; conserva la preferenza. Sette scenari UBSan RED, dieci GREEN e controllo nell'app con compilatore GLES reale. |

La correzione dello spinner riguarda errori e concorrenza riprodotti. Una
scansione normale dopo il caricamento delle icone può ancora richiedere tempo:
non è stato misurato un miglioramento del normale avvio né implementata una
cache speculativa. Servono endpoint distinti: libreria utilizzabile e scan finito.

Il setup BIOS conserva i file preesistenti e le preferenze valide se il candidato
non supera la verifica. Una copia interrotta non usa un nome canonico. Se il
provider rifiuta la cancellazione del temporaneo, il report ne mostra il nome;
un retry completo non lo adotta come NAND valida. Non viene promessa autenticità
della NAND, cancellazione istantanea di I/O non cooperativo o atomicità universale
contro scrittori esterni: SAF non offre ovunque rename-if-absent atomico.

## Verifica integrata e identità

- **267 test JVM/Robolectric**, zero fallimenti, errori o skip. Inclusi 30 BIOS
  e 24 ricerca/navigazione/selettore; le calibrazioni usano la scala font reale.
- APK ARM64 e test APK compilati; **lint: 0 errori, 474 warning**.
- Suite ART finale: **52 pass e 3 skip opt-in per ambiente**, su API33/4KiB
  e API35/4KiB/16KiB. Il runner stampa 55 casi: i tre skip non sono pass.
  WorkManager reale è eseguito separatamente e passa su tutti e tre.
- Cinque eseguibili NDK preesistenti passano su API33/4KiB e API35/4KiB/16KiB;
  il sesto, RendererFallbackTest, passa dieci scenari su tutti e tre.
- Shader reali: su API33 GLES3.1, Software controllo, Compute e OpenGL passano
  tre invocazioni separate dell'app. Si osservano rifiuto di CompositorShader,
  fallback reale, due fasi nuove del programma DS originale, immagine presentata,
  preferenze immutate e teardown. Nessuna factory grafica è simulata in questa
  prova. [Riproduttore e limiti](../../../tools/android-tests/renderer-fixture/README.md).

APK app SHA256:
`72c3e4363f74da95e9e02df44fa3cba523ddf51ca2216e6992d220682926ec1b`.
Test APK SHA256:
`6bd051e69cf85f3c3b8699541ab7a1d4416d2b18abceb4af05b0a60f66f47bcb`.
Queste identità appartengono al candidato locale, non all'APK installata sulla Thor.
Frontend BuildID `f108ca62588fed32ae60805bed94bad54581d9cd`; firma debug verificata.
APK, test APK, simboli compressi, manifest e piccole prove sono conservati in
un unico archivio locale. Il riproduttore originale è generato dal sorgente.
[Comandi e criteri ripetibili](VALIDATION.md).

La prima prova del programma originale era coperta dal suggerimento Android
sullo schermo intero: errore di preparazione del banco, non bug dell'app. Anche
gli iniziali errori di permessi del provider di test, classpath e calibrazione
font sono separati dai RED di prodotto. La revisione ha ritirato l'ipotesi di
hang nel costruttore AssetFileDescriptor dopo verifica del comportamento reale;
nessuna correzione artificiale è stata aggiunta per quella ipotesi.

## Rifinitura visiva con Impeccable

Riusati Compose Material2, AppCompat Preferences e i token melonDS esistenti:
rosso principale, superfici chiare/scure e helper dei pulsanti/campi. Nessuna
migrazione di framework o nuova identità visiva. Il risultato BIOS raggruppa
DS/DSi, distingue mancanti, invalidi, errori e temporanei non rimossi, e propone
il prossimo passo. La ricerca non attiva un cheat selezionando un risultato.

Il confronto finale Android copre portrait320dp IT/font200%, landscape640dp
IT/font200% e portrait EN/font100%, con tastiera reale e azione Fine; i precedenti
confronti includono font130% e temi chiaro/scuro. In landscape estremamente
basso la tastiera lascia circa64dp: resta visibile il testo in modifica, mentre
le azioni tornano dopo Fine senza confermare la selezione. Lo scorrimento può
tagliare parte dell'etichetta fuori viewport; non viene ridotto il font utente.
Il confronto ha anche rilevato icone bianche su fondo chiaro dopo la modifica
degli inset. Corretto il solo window del dialogo, con ripristino alla chiusura;
nuove catture native confermano icone scure su chiaro e chiare su scuro.
TalkBack esplorato manualmente e navigazione gamepad fisica restano da verificare.

## Thor scollegabile e lavoro autonomo sul Mac

La chat Sacred Gold Plus aveva già acquisito APK/firma/BuildID, ROM/raw/BIOS,
configurazione, layout, controller, cheat, log e stato hardware. Il pacchetto
canonico è riusato senza duplicarlo. La chat V1.3.0 usa l'AVD della replica ROM:
non è stato modificato. Questa campagna usa solo propri banchi e dati sintetici;
la Thor può restare scollegata.

L'APK Thor PROFILING ha SHA256
`198505e4f50d9eafa2dbc3f941fcec83f0b747e23c95cb00efdb57859ce8afaa`;
il commit sorgente non è attestato. Non si attribuisce retroattivamente il suo
SIG11 alla specifica istruzione ora corretta. Il dispositivo è Android13/ARM64,
pagine4KiB, Adreno740v2 e configurazione Compute6x. Il Mac può eseguire Android,
Room/JNI e riproduttori, ma non replica la GPU né il consumo fisico.

I dati raccolti non bastano per affermare risparmi energetici: 22°C e4048mV
riguardano la batteria, non la CPU; ThermalHAL non era pronto; 401MHz GPU era un
campione, non una media. Presentazioni SurfaceFlinger anche in pausa non sono
frame emulati. Trace prive dei canali richiesti non dimostrano CPU/GPU attribuibili.

La prossima finestra fisica deve prima verificare che i canali producano dati:
startup con endpoint distinti, CPU per processo, memoria/PSS, frequenze e tempi GPU
se disponibili, serie termiche identificate e energia. Confrontare la stessa
scena, build ottimizzate attestabili, opzioni e qualità, alternando A/B dopo warmup.
Conservare campioni, unità, variazione ed esclusioni; niente percentuali inventate.

## Upscaling e successivo audit

Il percorso Compute è **OpenGL ES3.2/GLSL, non OpenCL**. La scala6x migliora il
raster3D e non aggiunge automaticamente dettaglio agli sprite2D. Le ipotesi
rimaste sono visibilità tra passaggi GPU, riuso delle texture e costo degli span.
Il [piano dell'esperimento di visibilità](../../superpowers/plans/2026-10-28-compute-visibility-experiment.md)
è pronto, ma non è una patch prestazionale applicata. Una stima di memoria logica
non è una misura di RAM risparmiata. Non ridurre scala o slot per simulare qualità
equivalente; lo screenshot interno256×384 non certifica qualità6x. Il port
upstream dell'upscaling2D richiede un lavoro strutturale separato.

I due finding Daybreak esistenti sono stati riusati e verificati. Non è stato
lanciato un secondo audit generico duplicato. Il prossimo mandato è circoscritto
alla diff immutabile e ai confini residui, con chiamanti, input raggiungibile,
controllo fallente, impatto e limiti; dettagli nel piano. La revisione indipendente
finale e la delta di contrasto sono registrate separatamente. Pubblicazione PR ancora subordinata
all'autorizzazione esplicita del maintainer.
