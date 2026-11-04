# Repository-wide defensive campaign: first closed unit

Date: 2 November 2026. This report records the repository-wide inventory and
the closed `CORE-LITERAL-A64` correction unit. It does not claim that the
repository is bug-free or fully secure.

## Identity and constraints

- Android app: `main` initially and currently based on
  `1cc24ead4ba0f1e733b5d828df6a4b23ccf081b0`, tracking `origin/main` at the
  same commit. Existing modified and untracked files were preserved.
- Android core: `main` initially at
  `403c4683c3c61ddb4aa2a1b44ab0ab1c21b6009a`, tracking `fork/main` at that
  commit. It was clean before this unit and now has local commit
  `f76940fc` (`Avoid folding untracked AArch64 literals`).
- The app gitlink initially selected that exact core commit. No branch,
  worktree, reset, stash, generic cleanup, push, PR, release or upstream
  communication was made.
- The consolidated NAND commits were not amended or rewritten. No APK was
  installed; no physical device, personal app data, NAND, firmware, BIOS,
  save, database, URI grant or signing configuration was accessed.
- Free space was about 20 GiB before the builds and remained above the 10 GiB
  gate. The existing host/NDK/Gradle build trees were reused.

## Independent Deep Security Scan

One independent repository-wide security review was started for the whole
checkout, including the core submodule. The review emitted no draft, scan ID, manifest,
findings or coverage within the 45-minute initial unit budget. The exact
terminal result after the bounded stop was `Script terminated`.

The scan is therefore **BLOCKED by budget**, not PASS. It was not restarted,
manually reconstructed or finalized. Manual review and runtime verification
continued as an independent track. The report makes no claim of completed
security coverage from that interrupted operation.

## Surface matrix

The inventory used source ownership, production build inclusion, reachable
callers and existing dated evidence. `Reviewed` below means mapped and sampled;
it is not exhaustive dynamic acceptance.

| Surface | Owners and representative production paths | Coverage in this campaign | State |
| --- | --- | --- | --- |
| Activity/ViewModel/Surface/EGL/process death | `EmulatorActivity`, `EmulatorViewModel`, `EmulatorSurfaceView`, `FrameRenderCoordinator`, `Surface*Renderer`, `OpenGLContext` | Source map; historical #1665 sequence reconciled | lead, Android callback/EGL proof missing |
| SAF/ContentResolver/grants/saves/states/BIOS/firmware | `UriPermissionManager`, `FileUtils`, `FileSystemSaveStatesRepository`, `SaveManager`, `ConfigurationDirectoryVerifier`, `BiosFileClassifier` | Source map; JVM/host suite and packaging | reviewed; #1594 remains unconfirmed |
| DSi NAND/import/export/replacement/recovery | `MelonDSNandJNI.cpp`, DSiWare repositories/view models, core `DSi_NAND.cpp`/`FATStorage.cpp` | Fresh host `nand-fat` and existing-save regressions | consolidated fix green at component level; ART/SAF and rename faults remain |
| JNI references/UTF leases/owner lifetime | `MelonDSAndroidJNI.cpp`, `MelonDSNandJNI.cpp`, `WfcSettingsJNI.cpp`, `JniEnvHandler` | Source map; historical #1678 evidence retained | no new finding; ART not rerun |
| ARM/Thumb/JIT AArch64/literals/DTCM/fastmem | core `ARMJIT.cpp`, `ARMJIT_A64`, `ARMJIT_Memory.cpp`, `ARM_InstrInfo.cpp` | Fresh RED/GREEN host and NDK execution | confirmed and corrected in this unit |
| CPU/DMA/RTC/reset/savestate | core `ARM*`, `DMA*`, `RTC*`, `NDS.cpp`, `Savestate*`; app `RtcSync` | Source map; fresh host RTC regression | RTC local policy green at component level; broader clock matrix open |
| GPU Software/OpenGL/Compute/presentation | core `GPU3D_Soft`, `GPU3D_OpenGL`, `GPU3D_Compute`; app renderer layer | Source map and assembled native payload | reviewed statically; no visual/runtime acceptance |
| Audio/Oboe/microphone/realtime/route | app audio callbacks and policy; Oboe is vendor | Fresh host audio/microphone tests; vendor separated | app integration controls green; route/device coverage open |
| ROM scan/ZIP/7z/parser/memory/provider input | `RomFileProcessor*`, `CompressedRomFileProcessor`, repositories/workers | Source map and JVM suite | reviewed; no new confirmed bug |
| Room/WorkManager/async/cancel/rollback | database repositories, `CheatImportWorker`, `RetroAchievementsSubmissionWorker` | Source map, JVM suite, merged manifest/lint | reviewed; no new confirmed bug |
| Controller/touch/hotkey/layout/external display | input setup, controller models, layout repositories, `SecondaryDisplaySelector` | Source map and JVM suite | reviewed; no device/UI execution |
| Cheat/RetroAchievements/DNS/Wi-Fi/multiplayer | app RA/cheat/WFC layers; rcheevos/enet are vendors; core Wi-Fi/netplay | Source map and JVM/host suite | integration leads retained; no vendor rewrite proposed |
| Manifest/exported/intents/FileProvider/untrusted input | product manifests and merged APK manifest | Lint plus merged-manifest/APK inspection | packaged surface recorded; no new exploit finding |
| Gradle/CMake/ABI/API/page size/R8/packaging/signature | `buildSrc/AppConfig.kt`, app Gradle/CMake, APK | JVM/lint/assemble; APK package, ABI and cert inspection | build verified; release/R8 and install not exercised |
| Dependencies/submodules/vendor boundary | core, Oboe, FAAD2, enet submodules; bundled rcheevos and core third parties | identity map and production inclusion review | vendor-only matches excluded unless Android integration is reachable |

Qt/SDL desktop, macOS frontend behavior, JIT x64 and unused frontend code were
excluded. Host tests below support the shared Android core; they are not Android
runtime validation.

## Closed unit: CORE-LITERAL-A64

### Finding and proof contract

- ID/origin: `CORE-LITERAL-A64`, derived from the local JIT audit and compared
  with official GitHub PRs #11/#12 and shared-core #2746/#2747.
- Owner/caller/consumer: `T_Comp_LoadPCRel` calls
  `Compiler::Comp_MemLoadLiteral`; the generated AArch64 block consumes the
  folded value and the guest program publishes it to main RAM.
- Reachable prerequisites: Android production enables the AArch64 JIT and
  literal optimization; an ARM9 Thumb PC-relative literal can address DTCM.
- Expected: each load reads the current DTCM word A, then B, then C.
- Observed before: with JIT/folding ON/fastmem OFF the result and R0 stayed A
  after B and C. Exit 1. Folding OFF and interpreter read A/B/C; the analogous
  ARM literal and DTCM mapping/remapping controls were green.
- First wrong transition: `LocaliseCodeAddress` returns zero for DTCM, but the
  direct Thumb helper still embeds the value. Zero denotes no invalidation
  owner and must not be foldable.
- Confutation 1: identical Thumb guest with folding OFF, green.
- Confutation 2: identical Thumb guest through the interpreter, green.
- Additional control: ARM PC-relative DTCM load, green. Fast memory remained
  OFF for the selected target.
- Proof level: shared-core component executed on host AArch64 and Android NDK
  API35 ARM64. No game attribution, ART integration or physical-device claim.

The official GitHub API was reread on 2 November. PRs
`rafaelvcaetano/melonDS-android-lib#11` and `#12`, and
`melonDS-emu/melonDS#2746` and `#2747`, remain closed and unmerged. #12/#2746/
#2747 align the second lookup after invalidation; #11 fixes a distinct
invalidation mask. Their patches do not reject this first unlocalisable fold.
Original authorship and the distinction between fixes are preserved.

### Minimal correction

`Comp_MemLoadLiteral` now returns `false` when `LocaliseCodeAddress` returns
zero. This makes the existing runtime load path handle addresses without an
invalidation owner. No decoder, persistent format, vendor or frontend code was
changed.

Local core commit: `f76940fc`. It has not been pushed or published.

### Reproducible commands and results

```text
REQUIRE_CORE_TESTS=1 ./tools/host-tests.sh
./tools/core-host-sanitize.sh
clang++ -std=c++17 -Wall -Wextra -fsanitize=address,undefined -g -O1 ...
./build/host-tests/JitInvalidationTest
cmake --build app/build/android-native-tests/arm64-v8a --target JitInvalidationTest --parallel 8
./tools/android-tests/run.sh --serial emulator-5554 --abi arm64-v8a --test JitInvalidationTest
./tools/android-tests/run.sh --serial emulator-5556 --abi arm64-v8a --test JitInvalidationTest
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home bash ./gradlew :app:testGitHubProdDebugUnitTest :app:lintGitHubProdDebug :app:assembleGitHubProdDebug --stacktrace --console=plain
```

| Layer | Before | After |
| --- | --- | --- |
| Host AArch64 ASan/UBSan | valid out-of-sandbox run: target RED, exit 1; controls green | target and controls PASS, exit 0 |
| Host full suite | first sandbox run invalid because `shm_open` was denied | exit 0; all named tests PASS except ROM differential SKIP |
| NDK ARM64 build | historical RED binary only | build PASS, ELF SHA-256 `d36384dd5497a0ddb1415b35dd524836971dfa44d87809f29f7b14cdb88bf5f2` |
| API35 ARM64 4 KiB | historical RED | executed PASS, exit 0; page size 4096 |
| API35 ARM64 16 KiB | historical RED | executed PASS, exit 0; page size 16384 |
| JVM/Robolectric | not causal to RED | `testGitHubProdDebugUnitTest` PASS |
| Android lint | not causal to RED | PASS; warnings retained in generated report |
| APK build/package | not causal to RED | GitHubProdDebug assemble and signature verification PASS |
| ART/instrumentation | not run | SKIP: no JNI/Kotlin behavior changed |
| Physical device/game | not run | SKIP: not needed and not authorized |

The Android runner found no residual `melonds-core-tests.*` directory before
each AVD was stopped. Repeated `ftruncate(EINVAL)` is an AVD fast-memory
diagnostic, not the oracle; the selected case asserts fastmem OFF.

Host `JitDifferentialTest` is **SKIP** because `MELONDS_TEST_ROM` was not set;
no ROM was introduced. Toolchain warnings that the selected macOS libc++ target
is no longer supported and existing Kotlin deprecations/unchecked casts remain
warnings, not a pristine-output claim.

## Packaging identity

- APK: `app/build/outputs/apk/gitHubProd/debug/app-gitHub-prod-debug.apk`
- SHA-256: `5bfe664e5d12441e5bb9c1e9771492e9d9bea584929439b12fa7f2f52a7e3e5e`
- package/version: `me.magnum.melonds.dev`, versionCode 47, versionName
  `2.1.6 GH`; minSdk 24, target/compile SDK 37.
- native ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`.
- signature: APK Signature Scheme v2, one Android Debug RSA-2048 signer;
  certificate SHA-256
  `41ad82cd4365c73195128975cf0b920699f8833093507957dc2d0f9d6bb0b4b1`.

This APK was inspected only. It was not installed or launched.

## Reconciled findings and leads

Confirmed and corrected here:

- `CORE-LITERAL-A64`: reproduced before, corrected with one helper guard, then
  green on host sanitizers and both Android NDK page-size targets.

Already corrected and freshly checked at the available level:

- NAND replacement/import preservation in core `403c4683` and app `1cc24ead`:
  the fresh full host suite passes `nand-fat` and
  `ExistingSaveReplacementTest`. No patch was reapplied. ART/SAF and injected
  rename/rollback/cleanup metadata faults remain open.
- RTC resume policy: the fresh host suite passes the production model. Menu,
  background, lid, host clock/timezone/DST, process restart and game-event
  compatibility remain separate questions.

Unconfirmed leads, not findings:

- App #1665 Surface/resume: JVM ownership sequence exists, but PID/process
  death, Surface generation, EGLSurface, `swapBuffers` and renderer evidence on
  Android are still missing.
- Concurrent NAND close: static lifetime suspicion only; needs blocked import,
  task removal/`onCleared`, usage count and JNI return observation.
- App #1594 raw save: length preservation, mixed bytes, complete commit, SAF
  fallback and guest protocol have not been discriminated.
- DSi logical quota: not equated with FAT free space, short write or replacement.
- Manifest/vendor signatures found by search are leads only; bundled Oboe
  samples and other vendor manifests are not packaged product components.

No newly examined lead was declared confuted merely because its issue is open
or closed. The four selected JIT PRs are already-resolved-or-distinct upstream
comparisons, not fixes for this exact cause.

## Artifacts, hashes and receipt

- Reproducer source SHA-256:
  `4af1fd18eea42710fd7a66db89e5dfce293c4446c99eeaef318b78e97bb949ae`.
- Fixed production source SHA-256:
  `2dc060183d40ca0d437d9c8ad0b1f77a254f76e0acff412d9900d035e3bdbbd6`.
- Host ASan/UBSan executable SHA-256:
  `0c2d301387dd7f297dda2913454300cf7f41de95b253171c6d30ddf9ced1f428`.
- Android NDK executable SHA-256:
  `d36384dd5497a0ddb1415b35dd524836971dfa44d87809f29f7b14cdb88bf5f2`.
- Private receipt: `local/reports/ds-2026-11-02/repository-campaign/receipt.json`.
  It is validated offline with the retained local verification tools at revision
  `d8a11722f5cc0535f4d58ea6a1ab52c214797fdb`; receipt consistency does not
  certify security.

## Residual priority and future draft delta

1. NAND rename/rollback/cleanup fault injection and isolated ART/SAF import.
2. App #1665 real Android Surface/EGL lifecycle instrumentation.
3. Concurrent NAND close with a blocked import and owner-usage observation.
4. App #1594 raw-save commit protocol and SAF fallback.
5. RTC clock/timezone/process matrix and DSi logical quota.
6. Continue remaining static leads from the surface inventory; do not rewrite
   vendor code without a demonstrated Android integration crossing.

Future unpublished core draft delta: add one bullet stating that the AArch64
literal helper refuses addresses without an invalidation owner, with the dated
host/NDK red-green evidence. The app draft needs only the eventual gitlink
update after the core commit; no frontend source claim belongs to this fix.
Publication remains blocked pending explicit authorization.
