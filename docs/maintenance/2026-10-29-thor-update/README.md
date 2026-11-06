# Thor update and physical checks — 29 October 2026

**Retention update, 3 November:** the reproducible local 2.1.5 candidate APK was
retired after the [2.1.7 candidate checks](../2026-11-03-nightly-2.1.7/README.md).
The two retained update APKs are now 2.1.7 and the verified 2.1.6 fallback.
The dated measurements, hashes, source history and original data backups below
remain historical evidence; no published release asset was removed.

The installed modified Nightly was updated **in place from 2.1.4 to 2.1.5**.
The package, signing certificate, Android UID and original installation date
were preserved. The emulator was never uninstalled and its data was never cleared.
No push, release or pull request was published.

## What was installed

| Item | Value |
|---|---|
| Package | `me.magnum.melonds.nightly.perf` |
| Version | 2.1.5 GH (NIGHTLY) (PROFILING), versionCode 46 |
| App source | `b05cceb37bdc995a0871d8656cdd4d1ae0b85a9c` plus the versionCode/name change to 46/2.1.5 |
| Core | `fd7582400bb7e35fe0d36dbb0c89b6766c1a8f2e` |
| Build | Optimized ARM64 frontend, `-O2`, `-DNDEBUG`, non-debuggable, not testOnly |
| Installed APK SHA-256 | `0ecc829c6510aa4422902a7b415916a03d1052562bc7530d9edbdfefdb2766aa` |
| Previous installed APK SHA-256 | `198505e4f50d9eafa2dbc3f941fcec83f0b747e23c95cb00efdb57859ce8afaa` |
| Signing certificate SHA-256, both builds | `41ad82cd4365c73195128975cf0b920699f8833093507957dc2d0f9d6bb0b4b1` |

The previous APK's exact source revision is not attested. It is a locally modified
Nightly, not the official upstream baseline. The separate
[official Nightly comparison](../2026-10-29-nightly-comparison/README.md) remains
the basis for the app/core PR drafts. This session changes only the version in
product source; it installs the already reviewed functional changes.

## Data preservation

- Captured complete credential/device protected app snapshots before installation,
  including SQLite WAL files, `no_backup`, preferences, cheats and WFC data. A small
  temporary instrumentation package with the same signing certificate captured
  files before the target's providers and Application initialized.
- Verified ZIP integrity, every archived file's size/SHA-256 and both SQLite
  databases with `integrity_check` on copies. Database comparison includes
  ordered row hashes, not just row counts.
- Immediately after installation, before normal launch: **all 17 private files
  were byte-identical**, both databases matched and all three persisted document
  grants were unchanged. Android removed two regenerable graphics cache files.
- After normal use and test cleanup: both databases still match; **709,658 cheats,
  4,255 games and 30,341 cheat folders** remain. WFC, controller mappings and layouts
  are byte-identical. Preferences differ only in `last_version`, 45 → 46.
- All **46 externally referenced original files**, totaling 2,591,425,618 bytes,
  match their before-test hashes and verified Mac backups. This includes the ten
  original ROMs, raw saves, savestates, cheat XML and BIOS/firmware/NAND files.
- The ten original library records are identical to the pre-install snapshot.
  An earlier manual session on 2.1.4 changed one game's play-time metadata; that
  legitimate change was preserved. The temporary benchmark record was removed
  without resetting the library or its settings.
- Expected runtime profile markers changed. Test ROM/save files, device-side
  backup copies and temporary helper/test packages were removed. The original
  screen timeout and stay-awake setting were restored.

Backups and personal screenshots remain private under
`local/notes/thor-update-2026-10-29/`; no ROM, save, BIOS, account data or database
is part of this report's Git contents. The old APK remains a reference/fallback
artifact; a lower versionCode may prevent a direct Android downgrade, so this is
not a claim of a tested one-click rollback.

## Checks performed

| Check | Result and scope |
|---|---|
| JVM suite | **267 passed**, 0 failures/errors/skips, 43 suites |
| Profiling build and lint | Build passed; **0 errors, 474 warnings** |
| Physical Thor ART suite | **52 passed, 3 opt-in assumption skips** |
| Real WorkManager, separate process | **1 passed**: cancellation, notification removal and subsequent unique KEEP import |
| Android native executables | **6 passed** on Thor Android 13 / ARM64 / 4 KiB pages |
| Normal raw save | On a copy: in-game SAVE, process restart, title screen and Continue; updated play time persisted and the town loaded |
| Display/session checks | Both displays visible after normal launch, cheat browsing, pause/resume and one screen-off/on cycle |
| Cheat UI | Search and grouped 25-option selector opened; confirmation disabled before a selection; Cancel preserved database contents |
| BIOS UI | Guided setup introduction and existing folders displayed; cancelled before choosing a folder or writing files |

The six native targets are `JitInvalidationTest`, `MicInputGainTest`,
`MicSourceSwitchTest`, `NandJniTest`, `StorageNandTest` and `RendererFallbackTest`.
They were freshly cross-built with NDK 28 and executed in disposable device
directories. The renderer harness tests controlled failure paths; it is not a
claim that the Thor's real GLES driver failed and recovered.

ART tests used the separate `.nightly.dev` diagnostic package so malformed imports,
provider cancellation and BIOS-copy failures could not affect personal data.
Its APK hash is `72c3e4363f74da95e9e02df44fa3cba523ddf51ca2216e6992d220682926ec1b`;
test APK hash is `6bd051e69cf85f3c3b8699541ab7a1d4416d2b18abceb4af05b0a60f66f47bcb`.
All 56 recorded refinement source files still match that diagnostic build.
These runtime tests support the shared source; functional checks and measurements
above used the installed optimized 2.1.5 APK. Do not treat different build modes
as the same binary. See the [test contracts](../2026-10-28-refinement/VALIDATION.md).

## Before and after observations

Each series has one excluded warmup and five measurements. Both builds used ART
`speed` compilation. Gameplay used the same copied ROM/raw save, stationary town,
Compute 6x, JIT and threaded rendering, with no input during each 30-second window.
Hardware: AYN Thor, Android 13, Adreno 740; the active dual-display configuration
and renderer settings were retained across the update.

| Metric | Previous 2.1.4, median [range] | Installed 2.1.5, median [range] |
|---|---|---|
| Cold Activity launch, ms | 156 [152–165] | 154 [151–154] |
| Process CPU seconds per approximately 30.08 s | 11.63 [11.61–11.69] | 11.20 [11.09–11.57] |
| Endpoint total PSS, KiB | 575,569 [575,086–576,597] | 565,800 [565,172–565,824] |
| Endpoint total RSS, KiB | 703,240 [702,864–704,272] | 701,744 [701,116–701,768] |
| Battery temperature, °C | 23 | 25 |

Raw numerical samples: [CPU and memory](CPU-MEMORY.csv), [startup](STARTUP.csv).
CPU comes from process `utime + stime` divided by `CLK_TCK`, with device uptime
read bounds. It is not guest FPS, GPU time or energy. PSS/RSS are `dumpsys meminfo`
endpoints, not peak memory or a leak test.

**These are observations, not proven optimization gains.** Runs were sequential,
not alternating/randomized; the device warmed and the game's real-time lighting
changed from day to evening. The charging battery stayed at 100%, so no energy
savings can be inferred. Thermal HAL was unavailable; status zero is not proof
of no throttling. No validated CPU voltage, GPU utilization or memory-write-rate
measurement was obtained.

Both valid startup series were classified COLD, with ten original library entries.
`am start -W` measures Activity startup, not ROM-scan/spinner completion. Screenshots
two seconds after launch show populated lists; they do not quantify spinner time.
An intermediate WARM series was rejected: the temporary snapshot instrumentation
was followed by process restarts. After removing the helper, the process stayed
stopped and the COLD series was repeated. That artifact is not an app performance
regression. Its underlying Android/vendor restart mechanism was not established.

## Remaining work and limits

- **Intermittent black upper screen:** seen before the update and confirmed by
  the owner; closing/reopening recovered. Not reproduced during the successful
  2.1.5 checks, but no controlled reproducer or verified fix exists. A black upper
  screen while the game's lower-screen Continue menu is visible is normal and
  must not be counted as that defect.
- **Selector wording:** the nature selector still uses generic Pokémon/Pokédex
  search labels. The controls work, but the labels should describe the selected
  family. This minor UI issue was recorded, not silently counted as fixed.
- **Removed-file cache:** moving the benchmark outside its granted tree caused
  the storage provider to throw on lookup. The conservative scan guard retained
  its metadata. Distinguish confirmed deletion from temporary provider failure
  before changing this behavior; dropping settings on an ambiguous error is unsafe.
- Physical microphone capture, audible audio-resume judgment, external controller
  peripherals, hinge closure, manual TalkBack, multiplayer servers and every game
  are not covered by these checks. Screen power cycling is not a hinge test.
- No sustained 15-minute thermal comparison, battery comparison, new OpenCL/2D
  upscaler or image-quality improvement is claimed. No existing user savestate was
  loaded; its bytes were preserved. Raw-save compatibility was exercised on one copy.
- On the disconnected Mac, replay JVM/NDK/ART tests and copied save scenarios.
  Device settings and snapshots preserve the context, not the Thor's physical
  Adreno driver, thermal response, battery behavior or input latency.

## CI and publication

Checked on 29 October: [app CI](https://github.com/Umberto-DEV/melonDS-android/actions/runs/40725003607)
is green at `c543c367`; [core CI](https://github.com/Umberto-DEV/melonDS-android-lib/actions/runs/40696625134)
is green at `fd758240`. The new app commits/version are local and have not run in
remote CI. Local passing tests do not make that exact new commit remotely green.

- [Current app overview](../../../README.md#whats-new)
- [Current core overview](https://github.com/Umberto-DEV/melonDS-android-lib#whats-new)
- [Integrated features and per-issue coverage](../2026-10-28-refinement/INTEGRATION-REPORT.md)

No upstream issue or PR was closed by this session. Publish only after explicit
maintainer approval, with fresh CI on the exact published revisions.
The [independent review](REVIEW.md) accepted this scoped update and evidence,
with the coverage limitations above retained explicitly.

## Storage and handoff

Coordinated retention with the Sacred Gold Plus 1.3.0 work: no other build or
capture was active on the shared paths. Removed this run's reproducible Android
build/CMake intermediates after preserving reports and checking the canonical
APK. This freed **1,494,609,920 allocated bytes** (about 1.39 GiB); approximately
59.5 GiB remained free. The earlier central cleanup was not repeated.

Keep the current 2.1.5 APK, the canonical previous 2.1.4 APK, the separate diagnostic
app/test pair, original fixtures and unique evidence. The verified private backup
manifest references existing byte-identical copies rather than duplicating ROM
trees. The console is available for normal use and can be disconnected.
