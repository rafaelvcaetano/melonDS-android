# Thor update to modified Nightly 2.1.6

The maintainer explicitly authorized the physical update on 1 November 2026,
with settings, cheats and existing games preserved, followed by coordination
with the separate Sacred Gold Plus 1.3.1 owner. No publication is authorized.
This supersedes the installation gate in the original delivery mandate.

## Installed identity

| Item | Verified value |
| --- | --- |
| Device | AYN Thor, Android 13 / API33, ARM64, 4096-byte pages |
| Package | `me.magnum.melonds.nightly.perf` |
| Before | Modified 2.1.5 / versionCode 46 |
| After | Modified 2.1.6 / versionCode 47 |
| Installed APK SHA-256 | `2dc30348bc52d412b6bd8ac6e41774c012ad44e566e01011838b1160b62d05a0` |
| Certificate SHA-256 | `41ad82cd4365c73195128975cf0b920699f8833093507957dc2d0f9d6bb0b4b1` |
| UID | 10126, unchanged |
| First installation | 12 September 2026, unchanged |

The installed APK hash was read from Android's actual package path and matches
the [immutable candidate](../2026-10-31-nightly-2.1.6/README.md). Its optimized
ARM64 frontend, manifest, signature and prior JVM/build/lint/NDK evidence retain
their exact identities. No product source was changed or rebuilt for this update.
Installation used `adb install -r --no-incremental`; the target was never
uninstalled and its data was never cleared.

## Preservation evidence

- Fresh credential/device-protected snapshots precede installation, using the
  same signed instrumentation helper as the previous update. It archives before
  Application/providers initialize, then stops. ZIP CRC, all manifest sizes and
  SHA-256 values, SQLite integrity and ordered database row hashes were checked
  on copies. The helper was subsequently uninstalled; no cleanup instrumentation
  was invoked.
- Immediately after installation, all **17 credential-protected files are
  byte-identical**. Both database contents, the ten original library records,
  all three persisted document grants, UID and installation date are unchanged.
  Android invalidated two regenerable graphics code caches in device-protected
  storage; those contain no saves, settings or cheat databases.
- After the first normal launch, database contents, library, WFC, layouts and
  controller configuration are unchanged. All **709,658 cheats, 4,255 database
  games and 30,341 cheat folders** remain. Preferences differ only in
  `last_version`, 46 to 47. Two runtime profile markers changed as expected.
- Preservation does not imply that old codes are compatible with a new ROM.
  Before this update, the installed Sacred Gold Plus header identified as
  `IPKE/45F06230`, with no exact matching cheat database record; five selected
  cheats belong to the older `IPKE/1C1F741C` entry. That mismatch predates the
  emulator update. Records and selections remain intact; the ROM owner must
  validate any new header association and code compatibility before activation.
- All **47 protected external files**, **2,613,769,582 bytes**, match their
  pre-update SHA-256 values and verified Mac backups after the emulator update.
  These include the current ROMs, raw saves, savestates, cheat XML, BIOS, firmware,
  NAND and RetroArch raw saves. Existing identical canonical backups were reused.
- The current Sacred Gold Plus ROM and raw save differ from the 29 October
  snapshot. Both received fresh verified backups; the older progress was never
  restored over the current save. Six historical macOS metadata paths were
  already absent before this session; no actual historical BIOS/save/ROM path
  was missing.

Private evidence and executable procedures are under
`local/notes/thor-update-2026-11-01/`, including `external-backup-manifest.json`,
`after-install-private-comparison.json`, `after-launch-private-comparison.json`
and `external-after-install-verification.json`. Personal data is not embedded
in this public report.

## Physical execution and limits

The installed optimized app opens its populated library. A screenshot and the
resumed Activity establish that observation. The command-line UI hierarchy dump
repeatedly returned a null root on this dual-display device; it is not recorded
as a passing UI automation test. Initial black captures were taken during screen
suspension/wake transition, not evidence of the reported gameplay rendering bug.

All **ten native executables pass on the physical Thor**: `JitInvalidationTest`,
`MicInputGainTest`, `MicSourceSwitchTest`, `NandJniTest`, `StorageNandTest`,
`UtfLeaseTest`, `RendererFallbackTest`, `RtcResumeTest`, `NandFatTest` and
`ExistingSaveReplacementTest`. Exact binary hashes, exits and logs are recorded
in `android-native-results.json`. Each used its own temporary directory and
synthetic data; no personal firmware/NAND or game save was used.

These are NDK native tests, not ART instrumentation, hardware microphone tests,
Activity lifecycle/SAF fault tests or bootable DSi HOME tests. The new APK's
library launch does not certify every game, JNI string leases in ART, logical
HOME quotas, power-loss recovery or metadata-write failures during rename.
The earlier 52-test ART campaign belongs to the earlier diagnostic build.
No startup/performance, battery or graphics improvement is inferred.

An [independent read-only review](REVIEW.md) verified the retained archives,
canonical backups, native binary/log identities and selective cleanup. It accepts
this completed stage with the limits above, separately from later ROM work.

## Storage inspection and ROM handoff

Both mounted shared volumes were inventoried: **27,998 accessible files**, with
no discovery errors. The duplicate audit examined 156 game/save/BIOS/archive
files and 105 same-size candidates. Different first/middle/last samples reject
equality; every surviving collision is checked by full-file SHA-256. The
resulting 16 groups include intentional BIOS copies for separate emulators and
identical initial saves for different games: those are not disposable duplicates.
Different formats/revisions are not proven equivalent by this byte-level audit.

Only exact redundant ROM copies in the SD's macOS Trash are eligible for removal;
all active ROMs and every original save, including empty placeholders, are kept.
An automatic approval review rejected a broader cleanup that included two empty
original `.sav` files. That action did not execute; the accepted cleanup was
narrowed to Trash ROM copies only. Unique older ROMs are retained, not classified
as junk solely from age/name. Deletion manifests precede removals and identify
the hash-verified canonical files.

Removed **four byte-identical GBA ROM copies from Trash, 83,886,080 bytes**;
the active files were checked before and after removal. No original `.sav`
was deleted. All **19 original raw saves and savestates** were subsequently
rehashed and remain byte-identical, including the current Sacred Gold Plus save.

Six device-side snapshot transport archives were removed only after checking
their retained Mac copies; the empty directory created for UI dumps was removed
with `rmdir`. Native runners removed only their own executables/directories.

## Sacred Gold Plus handoff and final acceptance

The separate ROM workflow completed the authorized replacement and returned
device ownership with its producers stopped. The final single ROM remains at
`/storage/692A-CF9C/ROMs/nds/Pokemon - Sacred Gold Plus.nds`: **V1.3.1 IT**, the
manual-test candidate with bug fixes and six activation flags, 158,786,412 bytes.
SHA-256: `420ec8b8d5dbf30d209849bf18a439046a666a710656e01966a1f217d70f059a`.
The previous ROM has its verified canonical backup off the console. The original
raw save, its basename and path remain unchanged; its SHA-256 is still
`9c0cde6d7580cd1d227762f478c50de7346a2ae37545c9a6a411eac3feafb95c`.

On the physical Thor, a separately named copy of that current save completed
Continue, actual field loading, a normal in-game SAVE, process termination,
cold Continue and return to the field. The personal save was never opened by
the game during validation. Source tracing verified that the global SRAM
directory applies (`use_rom_dir=false`); simply placing a copied save beside a
ROM elsewhere would not isolate it. The saved copy and cold-restarted copy are
byte-identical. Generation advances once and the native save bank alternates.
An independent host decode of the retained bytes confirms the complete party,
box and daycare records, their lengths, and profile fields apart from elapsed
time. That decode supports the retained data; the screenshots/actions, not the
host parser, establish physical Android execution.

The ROM workflow additionally compared native/expanded inventory, existing Plus
payload, training jobs and shiny data. Its **strict complete auditor remains
NOT PASS**, recording changes in native variable/seed arrays 4, 14, 19, 21 and
34. Those differences were not suppressed to manufacture blanket acceptance.
This is one exercised IT save/load/save/restart case, not validation of every
save, language, new mechanic, battle/event state or performance claim. Resume
normally with Continue; the original older savestate was preserved but not
loaded or certified against the replacement ROM.

The integrated app backup proves unchanged exportable preferences, byte-identical
controller/layout files and all ten personal per-ROM configurations after the
ROM tests. A test-only library entry was removed using the app's existing restore
function with a payload containing **only `rom_data.json`**. The payload preserved
all ten current original records, including the new SGP RetroAchievements hash;
no preferences, cheats, controllers, layouts or saves were in the restore.
After restart, a new export matches that payload and contains exactly ten games,
no laboratory entry. Its remaining three JSON files are identical to the earlier
export. Independent comparison confirms these results. Transport/export files
were removed only after verified canonical copies and manifests.

An additional helper installation requested during the ROM stage was rejected
by automatic approval review because it would install another APK with private
data access. It was not installed or retried. Consequently, **no complete private
or cheat-database comparison was repeated after the ROM stage**. The complete
app-update snapshots above remain valid for their earlier stage; the built-in
export excludes the cheat database, WFC binary, RA session and storage paths.
The external cheat XML is unchanged. Old cheat records were not imported,
reassigned or enabled; new header `IPKE/B5D0B02A` does not validate old codes.

Final independent verification checked the ROM owner's full before/after results
against all **47 original protected paths and canonical backups**, with only the
SGP ROM replaced. It also freshly rehashed the new ROM and **all 19 original
saves/savestates on the device**, plus the installed APK. All match. The three
original persisted URI grants, package, UID and first-install date are unchanged;
the same ten NDS paths remain across internal storage and the mounted SD, with
one SGP ROM and no laboratory ROM. The helper is absent. The library was verified
after waking the normally sleeping screen; no personal game is running.

Final evidence: `final-device-identity.json`, `final-external-verification.json`,
`final-library-and-grants.json`, `post-rom-final-export-review.json` and
`independent-save-copy-review.json` in this update's private evidence directory.
ROM-project evidence is canonical under
`sacred-gold-plus/local/notes/thor-1.3.1-update-2026-11-01/` (`REPORT.md`,
`RESULT.json`, strict audit, snapshots and action manifests). Its public validation
record is `source/docs/ui-1.3.1-validation.md` at local commit `db6a3c5` in that
repository. No ROM sources or outputs were changed by the emulator workflow.

The update leaves approximately **38 GiB free on the Mac**. The canonical APK,
verified fallback, current original save/ROM backups and unique test evidence
remain. All app/core source changes and pre-existing work remain on main.
No push, PR, release or upstream message was published.

- [App PR draft](../2026-10-28-main/NEXT-APP-PR.md)
- [Core PR draft](../2026-10-28-main/NEXT-CORE-PR.md)
