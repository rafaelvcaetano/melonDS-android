# Procedura AVD e Thor — melonDS Android (ventuno)

> **Procedura storica del 12 settembre, superata per gli aggiornamenti.**
> Non eseguire le disinstallazioni o il ripristino descritti nelle sezioni 5–6:
> cancellerebbero dati e permessi. La firma attuale permette un aggiornamento
> in-place. Seguire il [resoconto 2.1.7 e la procedura corrente](../maintenance/2026-11-03-nightly-2.1.7/README.md).
> Versioni, percorsi SDK/AVD e identità Thor sotto sono storici. Conservati come
> prova, non come comandi da applicare alla configurazione attuale.

Documento operativo per un agente che esegue passo passo. Basato su fatti verificati il 12/09/2026
(vedi `PROVE-AVD.md`, `STATO-VENTUNO.md`) e sul codice. Non contiene istruzioni per toccare il
dispositivo fisico 20e51bf7 (Thor) né per avviare l'AVD in questa sessione: chi esegue questo
documento deve chiedere conferma esplicita prima di qualunque comando sulla Thor (sezione 5) e prima
di installare build.

## 1. Prerequisiti e regole

- **Sempre `-s <serial>` con adb.** Possono essere collegati sia l'AVD (`emulator-5554`) sia la Thor
  (`20e51bf7`, AYN Thor, Android 13, Adreno 740, melonDS 2.0.1 da Obtainium). Senza `-s` un comando
  fallisce o colpisce il device sbagliato: MAI ometterlo.
- **Mai cancellare senza backup verificato**: APK installate, ROM, salvataggi, savestate,
  preferenze, sia sull'AVD sia (soprattutto) sulla Thor.
- **Thor: sola lettura salvo richiesta esplicita.** Nessuna install/uninstall senza che l'utente la
  chieda per quella sessione. `me.magnum.melonds` (2.0.1) non si tocca mai.
- **Mai avviare l'AVD senza che l'utente lo chieda.** Le sezioni 2-4 sono da eseguire solo su
  richiesta esplicita di una nuova campagna di prova.
- **Regola disco:** cancellare APK/ROM/log grezzi a fine prova; tenere solo screenshot decisivi.
- Percorsi SDK: `~/Library/Android/sdk/emulator/emulator`, `~/Library/Android/sdk/platform-tools/adb`.

## 2. AVD passo passo

AVD esistente: `melonds-test` (Pixel 6, API 35 arm64) in `~/.android/avd/`.

1. Avvio (aggiungere `-no-audio` se non serve; **non** usare `-gpu swiftshader_indirect`, non rende
   OpenGL comunque, vedi §3):
   `~/Library/Android/sdk/emulator/emulator -avd melonds-test -no-window -gpu host -no-snapshot -no-boot-anim &`
2. Attesa boot: `adb -s emulator-5554 wait-for-device`, poi ripetere
   `adb -s emulator-5554 shell getprop sys.boot_completed` finché non risponde `1`.
3. Installazione APK debug 2.1 (`-t` perché test-only; `-r` conserva dati/permesso SAF se già
   installata; usare sempre `--no-incremental` — con build wrap.sh/HWASan la modalità Incremental di
   default non estrae `wrap.sh` in `lib/arm64/` e l'istrumentazione non si attiva):
   `adb -s emulator-5554 install -t -r --no-incremental ~/Developer/android-test/apk/ventuno-2.1-20260912/debug-avd/me.magnum.melonds.nightly.dev-ventuno-app_2db22d30-core_bc060f4c-debug-arm64.apk`
   Package: `me.magnum.melonds.nightly.dev`.
4. ROM/salvataggi in `/sdcard/Download/roms/`: `adb -s emulator-5554 push <rom_locale> /sdcard/Download/roms/`
5. **Concessione cartella ROM (SAF), una volta per installazione**: Impostazioni → ROM directories →
   picker di sistema → `Download/roms` → "USE THIS FOLDER" → "ALLOW". Salta se già presente (vedi
   passo 7). Disinstallare il pacchetto fa perdere il permesso: va rifatto dopo ogni reinstallazione
   pulita.
6. Navigazione UI senza schermo: `adb -s emulator-5554 shell uiautomator dump /sdcard/ui.xml` +
   `adb -s emulator-5554 pull /sdcard/ui.xml .`, leggere `bounds="[x1,y1][x2,y2]"` e tappare al
   centro con `adb -s emulator-5554 shell input tap <x> <y>`.
7. Verifica preferenze (sola lettura):
   `adb -s emulator-5554 shell run-as me.magnum.melonds.nightly.dev cat shared_prefs/me.magnum.melonds.nightly.dev_preferences.xml`
8. Scrittura mirata di una preferenza, dopo `am force-stop` (`sh -c "..."` come un unico argomento a
   `run-as`):
   ```
   adb -s emulator-5554 shell am force-stop me.magnum.melonds.nightly.dev
   adb -s emulator-5554 shell run-as me.magnum.melonds.nightly.dev sh -c \
     "echo <base64> | base64 -d > shared_prefs/me.magnum.melonds.nightly.dev_preferences.xml"
   ```
9. Lancio esterno di una ROM (dopo il permesso SAF), URI `content://` URL-encoded — **non** `file://`
   (fallisce per scoped storage, `EACCES`):
   `adb -s emulator-5554 shell am start -n me.magnum.melonds.nightly.dev/me.magnum.melonds.ui.emulator.EmulatorActivity -d "content://com.android.externalstorage.documents/document/primary%3ADownload%2Froms%2F<nome>.nds"`
10. Screenshot: `adb -s emulator-5554 exec-out screencap -p > file.png`
11. Logcat mirato: `adb -s emulator-5554 logcat -d | grep -a -E "melonDS|FATAL|AndroidRuntime"`
12. Chiusura: `adb -s emulator-5554 emu kill`, poi `pgrep -f qemu-system` — se ancora vivo (osservato
    fino a ~11s dopo `emu kill`) `pkill -9 -f qemu-system-aarch64`.

## 3. Limiti noti dell'AVD

- **Renderer OpenGL nero.** Con `video_renderer=opengl` lo schermo resta nero per tutta la sessione
  (`GL error 0x501/0x502` lato host, gfxstream), anche con l'APK del ramo PR (byte-identica): limite
  dell'ambiente, non regressione dell'app — va riprovato sulla Thor.
- **Configurazione che rende nell'AVD:** `video_renderer=software`, `video_internal_resolution=1`,
  `video_filtering=none`. A `video_internal_resolution=4` con OpenGL l'immagine è corrotta.
- **HWASan** richiede `wrap.sh` in `lib/arm64-v8a/` e installazione `--no-incremental`; senza,
  crash immediato `UnsatisfiedLinkError` su `libclang_rt.hwasan-aarch64-android.so`.
- **`am start -a android.intent.action.VIEW`** non risolve nessuna Activity (manifest dichiara solo
  `${applicationId}.LAUNCH_ROM`, senza `<data>`): usare `-n .../EmulatorActivity -d <uri>` (passo 9).
- **`dalvik.vm.heapgrowthlimit` non coincide col valore in `config.ini`** (osservato: 192m runtime
  contro 256M scritto): rileggere sempre a runtime con `getprop`, non fidarsi del config.

## 4. Convenzione ROM ↔ salvataggio ↔ savestate ↔ cheat (verificata nel codice)

- **Salvataggio SRAM (.sav).** Nome derivato da quello della ROM sostituendo l'estensione con `.sav`
  (o accodandola se senza estensione): `app/src/main/java/me/magnum/melonds/impl/emulator/SramProvider.kt:20-21`
  (`romFileName.replaceAfterLast('.', "sav", "$romFileName.sav")`). Cercato/creato nella cartella di
  `getSaveFileDirectory(rom)`: cartella della ROM se `use_rom_dir=true` (default), altrimenti
  `sram_dir` configurata — `SharedPreferencesSettingsRepository.kt:412-438`. **Conseguenza**: una ROM
  con nome diverso (es. copia "1.2") cerca un `.sav` con quel nome; per riusare un salvataggio
  esistente ("1.1") serve una copia rinominata del `.sav`.
- **Savestate (.ml0-.ml8).** Nome `<ROM senza estensione>.ml<slot>` (slot 0 = quick save), stessa
  cartella (default `SaveStateLocation.SAVE_DIR`): `FileSystemSaveStatesRepository.kt:28` (regex
  `"${romFileName}\\.ml[0-8]"`), `:55` (creazione), `:94` (quick save `.ml0`). Stessa conseguenza:
  ROM rinominata non trova i savestate originali, servono copie rinominate.
- **Cheat XML/.dat.** Nessuna ricerca automatica per nome ROM/cartella: importazione esplicita da un
  file scelto via SAF (`CheatImportWorker.kt:50-72`), parser scelto per estensione — `.xml` →
  `XmlCheatDatabaseParser`, `.dat` → `UsrCheatDatabaseParser` (righe 66-71) — salvati nel database
  Room (`CheatsRepository`/`CheatDao`), non nel filesystem accanto alla ROM.
- Chiavi video verificate: `video_renderer` (`software`/`opengl`/`compute`, `arrays.xml:32-36`),
  `video_internal_resolution` (`"1"`.."8"`, righe 38-47), `video_filtering` (`none`/`linear`/`xbr2`/
  `hq2x`/`hq4x`/`quilez`/`lcd`/`scanlines`, righe 49-58); UI in `pref_video.xml:7-37`; lettura con
  default in `SharedPreferencesSettingsRepository.kt:311-330`.
- **WFC "server consigliati"** (schermata Online connections): slot 1 = `5.161.56.11` (WiiLink, Doni
  Segreti), slot 2 = `178.62.43.212` (Kaeru, GTS/battaglie), slot 3 = `172.104.88.237` (AltWFC).
  **Non usare più `167.235.229.36`** per lo slot 1: verificato con `dig` il 12/09 che non risolve
  `dls1` (il dominio Nintendo WFC che il DS interroga per il download center); `5.161.56.11` è
  l'indirizzo WiiLink corretto per quel servizio.

## 5. Thor: installazione build 2.1 e verifica

**Solo su richiesta esplicita dell'utente per questa sessione.** Ogni comando tocca il dispositivo
fisico 20e51bf7. APK:
`~/Developer/android-test/apk/ventuno-2.1-20260912/me.magnum.melonds.nightly.perf-ventuno-app_2db22d30-core_bc060f4c.apk`
(package `me.magnum.melonds.nightly.perf`, non debuggable: niente `run-as`). SHA256 in `SHA256.txt`
nella stessa cartella.

1. **Backup preventivo**: verificare che esista `~/Developer/android-test/thor/backup-20260912/`
   con l'APK attualmente installato e i dati utili. Non procedere senza backup verificato.
2. Firma diversa da quella già installata (keystore precedente perduto) → uninstall obbligatorio
   prima di install:
   ```
   adb -s 20e51bf7 uninstall me.magnum.melonds.nightly.perf
   adb -s 20e51bf7 install -t ~/Developer/android-test/apk/ventuno-2.1-20260912/me.magnum.melonds.nightly.perf-ventuno-app_2db22d30-core_bc060f4c.apk
   ```
3. Verifica: `adb -s 20e51bf7 shell pm path me.magnum.melonds.nightly.perf` e
   `adb -s 20e51bf7 shell dumpsys package me.magnum.melonds.nightly.perf | grep -E "versionName|lastUpdateTime"`
4. Verifica firma APK (richiede `JAVA_HOME`):
   `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ~/Library/Android/sdk/build-tools/37.0.0/apksigner verify --print-certs <apk>`
5. La disinstallazione cancella tutte le preferenze: reimpostare a mano cartella ROM
   (`/storage/692A-CF9C/ROMs/nds/` — verificato oggi: ROM, `.sav`, `.ml0` insieme, es.
   `Sacred Gold Plus 1.1 IT.nds`/`.sav`, `Pokemon - Sacred Gold Plus.ml0`), cartelle BIOS DS/DSi
   (riferimento in `~/Developer/android-test/bios/`), video (Compute/OpenGL, scala), layout.
6. **Non toccare** `me.magnum.melonds` (2.0.1, Obtainium): resta indipendente da questa build.

## 6. Ripristino in caso di problemi

1. APK di backup: `~/Developer/android-test/thor/backup-20260912/apk/nightly.perf-installed.apk`.
2. Firma diversa dalla precedente installata → serve di nuovo uninstall → install, non `install -r`:
   ```
   adb -s 20e51bf7 uninstall me.magnum.melonds.nightly.perf
   adb -s 20e51bf7 install -t ~/Developer/android-test/thor/backup-20260912/apk/nightly.perf-installed.apk
   ```
3. Rifare la configurazione manuale del punto 5 (§5): la disinstallazione l'ha cancellata di nuovo.
4. Verificare `pm path`/`dumpsys package` (come §5.3) prima di dichiarare il ripristino concluso.
5. Se il problema persiste, fermarsi e riferire all'utente: nessuna ulteriore azione automatica.

## 7. Checklist finale

- [ ] Ogni comando adb con `-s emulator-5554` o `-s 20e51bf7`, mai senza `-s`.
- [ ] Nessuna cancellazione senza backup verificato.
- [ ] AVD: `emu kill` eseguito, nessun `qemu-system-aarch64` residuo (`pgrep -f qemu-system` vuoto).
- [ ] AVD: file grezzi di prova cancellati, solo screenshot decisivi conservati.
- [ ] Thor: nessuna operazione se non esplicitamente richiesta per questa sessione.
- [ ] Thor: se installata build nuova, permessi/config reimpostati e verificati.
- [ ] `me.magnum.melonds` 2.0.1 sulla Thor non toccata.
- [ ] Nessuna modifica a file tracciati da git in questa sessione, salvo istruzione esplicita.


> Nota (12/09 18:02): le APK costruite con `-Pandroid.injected.build.abi` sono marcate TEST_ONLY: senza `-t` l'installazione fallisce con `INSTALL_FAILED_TEST_ONLY`. Installazione della 2.1 sulla Thor eseguita alle 18:02 del 12/09/2026 con `adb -s 20e51bf7 install -t` dopo `uninstall` (firma diversa dalla precedente).
