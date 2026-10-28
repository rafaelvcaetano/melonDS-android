# Esperimento Android: visibilità dell’output compute

Stato: progettato, non eseguito. Nessuna modifica al renderer applicata.
Base app `c543c3675ef28d99b77749d391d135397d8adb8c`, core
`fd7582400bb7e35fe0d36dbb0c89b6766c1a8f2e`. Lavorare su main, senza PR.
La ricerca privata canonica è `local/notes/rendering-audit-2026-10-28/`;
non duplicarne patch e acquisizioni. Questo esperimento precede H2 (riuso texture)
e H3 (span/upload); non combinare le modifiche.

## Domanda e precondizione

Le scritture imageStore di FinalFB/LowResFB sono visibili rispettivamente al
sampler del compositor e al framebuffer della cattura? Il codice attuale
emette il bit shader-image ma non quelli texture-fetch/framebuffer per questi
consumatori. È una lacuna statica rispetto al contratto GL; nessun glitch Adreno
è stato riprodotto. Non è una diagnosi dei freeze della ROM.

Il banco deve prima compilare ed eseguire il compute effettivo GLES3.2. La
replica API33 disponibile fallisce su GLES3.0; il suo Software1x funzionante
non soddisfa la precondizione. Fermare questa prova se il banco non supporta
il percorso, registrando il limite. Non abbassare qualità/backend per dichiararla
passata. La Thor scollegata richiede una futura finestra coordinata.

## Superfici e modifica candidata

- `melonDS-android-lib/src/GPU3D_Compute.cpp`: FinalPass a fine RenderFrame,
  SetupAccelFrame e PrepareCaptureFrame.
- `melonDS-android-lib/src/GPU3D_Compute_shaders.h`: imageStore FinalFB/LowResFB.
- `melonDS-android-lib/src/GPU_OpenGL.cpp` e `_shaders.h`: compositor/texelFetch.
- `melonDS-android-lib/src/GPU2D_Soft.cpp`: consumer di cattura.
- Harness Android separato sotto `tools/android-tests/`, riusando toolchain e
  seriale esplicito; eventuale diagnostica dietro opzione disattivata normalmente.

La proposta privata aggiunge TEXTURE_FETCH al barrier finale e FRAMEBUFFER
all’ingresso PrepareCaptureFrame; conserva gli altri bit. Prima verificare che
il chiamante non fornisca già una barriera equivalente. Nessun glFinish,
cambio precisione, scala, texture pool o sincronizzazione non pertinente.

## Sequenza ripetibile e oracolo

1. Registrare hash APK/librerie, Build ID, sorgente+delta, GL vendor/version/
   estensioni, API/ABI/page size, opzioni effettive e fixture/input.
2. Eseguire sul percorso reale pattern alternati con generazione nota; consumare
   prima con compositor e poi con cattura. Readback diagnostico dopo il consumer,
   senza barriera/readback aggiuntivi prima che possano mascherare la lacuna.
3. Coprire clear senza poligoni, frame identici riusati, variazione output,
   cattura on/off, 1x diagnostico e 6x richiesto. Verificare LowResFB per dimensioni
   e valori. Registrare generazioni stale e pixel errati, non solo assenza di crash.
4. Ripetere baseline/candidato sullo stesso driver con la sola patch proposta.
   Se baseline passa, non inventare un RED: riportare conformità API e sintomo
   non riprodotto. Un test che ispeziona i bit non prova un errore di pixel.
5. Controllo funzionale su scene 2D, 3D e miste con input/guest frame fissati,
   entrambe le superfici e capture. Screenshot a dimensione composta reale:
   la funzione screenshot dell’app riduce a256×384 ed è insufficiente per6x.
6. Solo dopo correttezza, separare una campagna timing con build equivalenti
   ottimizzate/profileable, pilot dei contatori e A/B alternato. Nessun guadagno
   atteso implicito: barriere aggiuntive possono costare tempo.

## Accettazione e prosecuzione

Accettare una correzione visibile solo con baseline errata, candidato corretto,
controlli invariati e review. Una modifica di conformità senza glitch riprodotto
va presentata come tale. Nessuna percentuale CPU/GPU/energia da stime statiche.

H2 successivo mantiene nove slot e presenta la stessa generazione: prova di
ownership prima, poi allocazioni/byte richiesti, fence e latenza reali. La stima
128065536 byte per nove texture6x non è memoria risparmiata. H3 richiede prima
sezioni temporali distinte per preparazione span/upload/raster/compositor/JIT.

Riferimenti primari: [Khronos glMemoryBarrier](https://registry.khronos.org/OpenGL-Refpages/es3/html/glMemoryBarrier.xhtml),
[Android profileable](https://developer.android.com/guide/topics/manifest/profileable-element),
[Perfetto battery counters](https://perfetto.dev/docs/data-sources/battery-counters).
