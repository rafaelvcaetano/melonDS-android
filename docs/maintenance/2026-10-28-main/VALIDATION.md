# Verifica Android e consolidamento — 28 ottobre 2026

Il target è melonDS Android. La macchina di sviluppo compila mediante NDK; i risultati di esecuzione Android provengono da due dispositivi virtuali ARM64 con Android 15/API 35. I test Linux del core condiviso sono evidenza aggiuntiva, separata dai risultati Android.

## Stato del repository

- App e libreria hanno soltanto `main`, sia localmente sia nei fork GitHub di Umberto-DEV; entrambi i default sono `main`.
- Tutte le nove punte dei branch locali originali sono ancora antenati dei rispettivi `main`. Bundle, patch e file locali iniziali sono conservati nella directory privata dei backup.
- PR app #1682 e core #19 archiviate e chiuse. Nessuna nuova PR aperta. Le descrizioni future sono in `NEXT-APP-PR.md` e `NEXT-CORE-PR.md`, con fonti, crediti e riferimenti precedenti conservati.
- Una futura PR partirà dal nostro `main` e avrà come destinazione il default upstream `master`. La pubblicazione resta bloccata dall'utente.

## Verifiche e risultati

| Verifica | Ambiente | Risultato |
|---|---|---|
| Test JVM/Robolectric | Modulo Android, Gradle | 190 test, 0 fallimenti/errori/saltati |
| APK debug ARM64 | NDK 28.0.13004108 | Compilazione riuscita |
| Android Lint | Variante GitHub Nightly Debug | 0 errori, 474 warning |
| Regressioni JIT/DTCM, UBSan rigoroso | Android 15 ARM64, pagine 4096 | PASS, exit 0 |
| Stesse regressioni e binario | Android 15 ARM64, pagine 16384 | PASS, exit 0 |
| Installazione e avvio APK | Entrambi gli Android virtuali | Installazione e cold launch riusciti; processo vivo, nessuna FATAL EXCEPTION nel log del processo |
| Allineamento APK | `zipalign -c -P 16 -v 4` | Verifica riuscita |
| Suite native di supporto | CI Linux x64 e ARM64 | PASS: policy RTC/audio/input, modalità file, salvataggi, JIT, pacchetti DNS e lifetime riferimenti JNI |
| Core strumentato ASan/UBSan | CI Linux x64 e ARM64 | Build riuscita |

CI della revisione funzionale app `ca39ab6520b7062b2b4e85e5e2aacea94ebb9a57`: [esecuzione 40697791508](https://github.com/Umberto-DEV/melonDS-android/actions/runs/40697791508), completata con successo in tutti i job di verifica. CI core `fd7582400bb7e35fe0d36dbb0c89b6766c1a8f2e`: [esecuzione 40696625134](https://github.com/Umberto-DEV/melonDS-android-lib/actions/runs/40696625134), completata con successo. Il successivo commit di questo rapporto modifica soltanto documentazione; il controllo finale della chat verifica anche la sua CI.

I job di release/signing sono intenzionalmente esclusi nei fork. Il confronto opzionale con ROM commerciale non è stato eseguito: la suite sintetica non richiede ROM o BIOS esterni. Nessun controllo obbligatorio viene soppresso per ottenere un risultato verde.

## Regressioni osservate prima della correzione

Il percorso screen-off → stop perdeva la pausa differita; il nuovo controller ne preserva la scadenza e completa le pause pendenti al teardown. La coroutine della sessione partiva attiva prima dell'inizializzazione e il contatore FPS conservava vecchi vincoli. Questi tre comportamenti sono stati osservati fallire prima delle correzioni e passare dopo nei test JVM/Robolectric del modulo Android.

La CI rigorosa ha rilevato un accesso ad ARM9 dopo la sua distruzione durante la pulizia JIT. Il caso corrisponde alla precedente [segnalazione upstream #2537](https://github.com/melonDS-emu/melonDS/issues/2537). La pulizia dei mapping viene ora eseguita quando ARM9 è ancora viva; memoria sottostante e ordine dei componenti restano invariati.

La regressione DTCM è stata poi riprodotta su Android ARM64: il registro R0 conteneva il valore nuovo, ma la scrittura destinata alla RAM principale finiva nella DTCM. Disabilitare soltanto fast memory faceva passare il caso; disabilitare soltanto l'ottimizzazione literal lo lasciava fallire. La sentinella fisica DTCM ha dimostrato l'alias. Il test di rimappatura provocava inoltre un'asserzione nel codice iniziale.

La correzione limita il mirror alla finestra configurata, conserva l'offset fisico, rende simmetrici mapping/unmapping e invalida l'intervallo precedente al cambio di base. Sono coperti finestre da 4/8/16/32 KiB, entrambi i mirror del caso 32 KiB, movimento della DTCM e fallback delle finestre inferiori alla pagina Android. Le richieste di fast memory sono verificate esplicitamente nel test.

Le mancanze di `<memory>` in SaveManager e `<cstdarg>` nel test DNS sono state corrette quando la CI Linux ha reso visibili le dipendenze accidentali da altri header. I log dei tentativi falliti sono conservati insieme a quelli riusciti.

## Issue, metodo e limiti

Il [registro completo](ISSUE-CATEGORIES.md) contiene 246 issue aperte in 13 categorie funzionali, con stato nel codice e motivazione per voce nel JSON. La [ricerca JIT](KNOWN-JIT-ISSUES.md) distingue corrispondenze causali, patch già presenti e segnalazioni solo apparentemente simili. Non è stata trovata una patch pronta equivalente per il difetto DTCM: l'implementazione locale ha una regressione Android specifica.

Le prove native usano il core reale con servizi frontend sostituiti dal banco di prova. Non certificano Bluetooth, driver grafici, input fisico, storage provider, dual-display Thor o tutti i giochi. L'avvio dell'APK non è una sessione di gioco completa. Android x86_64 runtime non è stato eseguito; i risultati Linux x64 non lo sostituiscono. Leak detection è disabilitata nel banco Linux e il test DNS è funzionale, senza sanitizer.

Il core Android emette ancora un messaggio `ftruncate`/`EINVAL` nel percorso di allocazione condivisa, già presente nella baseline: non ha impedito i casi verificati e non viene dichiarato corretto da questo intervento. I precedenti differenziali specifici macOS sono fuori dalla verifica Android.

Il debug successivo resta coordinato con Sacred Gold Plus. Daybreak attende la conferma dell'utente sull'autenticazione; nessun tentativo è stato avviato qui. Le nuove PR rimangono bloccate fino alla successiva autorizzazione dell'utente.

## Evidenze riproducibili

Comandi del banco Android: `tools/android-tests/build.sh` e `tools/android-tests/run.sh --serial DEVICE_SERIAL`. SDK/API/ABI/dimensione pagina ed exit code vengono stampati dal runner; quest'ultimo elimina soltanto i propri file temporanei.

I log locali sono in `build/main-consolidation/` e copiati nel backup esterno, sottocartella `validation/`: `android-jit-baseline.log`, `android-jit-diagnostic-red.log`, `android-jit-remap-red.log`, `android-jit-final-4k.log`, `android-jit-final-16k.log`, i due smoke test APK, `apk-16k-zipalign.log`, `android-runtime-evidence.json` e i report Gradle/lint. Gli hash sono raccolti in `validation-manifest.json`. I dispositivi virtuali temporanei sono stati fermati e rimossi dopo le prove; gli SDK restano disponibili.
