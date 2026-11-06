# Remove a single ROM entry from the library

6 November 2026. The next UD Nightly release candidate is **2.1.10,
version code 51**, with production source
`bd546465abffb6e775b3b9f51bcf1f07224eec89`. The source correction, JVM suite,
release builds and lint have passed. Exact public-package and compatible Thor
candidate updates, selected-entry removal and cold persistence have also passed
within the Android coverage recorded below. Release publication remains
separate from candidate acceptance.

## Problem and functional impact

A deleted temporary ROM can remain visible in the library. Refresh scans for
new or changed files but does not remove existing entries. On startup, the
repository removes a cached content URI only when its metadata query succeeds
with an empty cursor. A null cursor or exception retains the entry because
unavailable storage and denied permissions do not prove that a file was deleted.

The affected baseline is app 2.1.9/50, source `d8f18d00`, with the same relevant
repository/UI code at `a2e7795e`. The retained baseline APK has SHA-256
`c66d46c5a53ae66f03515855b1fb70800d85baaad9dcd847d3224bf9af8736c1`.
Obsolete test-ROM entries were observed after file removal, refresh and process
restart. The precise SAF response responsible for those restart observations
was not independently captured; the source permits null/error retention, and
the regression uses an explicit denied-query provider case.

**Functional severity: moderate usability impact.** Stale entries clutter the
library and there was no supported way to remove only one of them. This is not
a demonstrated ROM/save corruption or security vulnerability; no CVSS score is
assigned. It does not establish that every provider retains deleted entries.

### Reproduction and negative control

1. Add a disposable ROM through a granted search directory and let its library
   entry load. Keep a second ROM with distinct per-game settings as a control.
2. Remove only the disposable ROM file outside the app, preserving its save
   and all unrelated files.
3. Refresh the library. The old implementation's scan is additive, so its
   in-memory entry remains.
4. After a cold app start, a successful empty metadata query removes the entry;
   a null result or access exception retains it. The latter path requires an
   explicit forget action rather than assuming that all access errors mean
   deletion.

Changing search directories triggers a library-wide rebuild. The cache also
holds custom settings, last-played time, total play time and save-name migration
metadata, so clearing it is not a selective cleanup. Clearing app data or
revoking shared directory grants is broader still. The current settings backup
intentionally excludes `rom_data.json`; supplying an edited copy to its restore
flow does not restore or edit the ROM library.

## Correction

The ROM details page now includes **Remove from library**. Its confirmation
shows the selected name and exact URI and explains that only that entry's
settings and play history are removed. ROMs, saves, save states, cheats and
other entries remain unchanged. If the ROM still exists, a later scan can add
it again; this action is not a permanent hide list. The new text is provided
in English, Italian, French, Spanish, Brazilian Portuguese, Russian,
Indonesian and Simplified Chinese.

`RomsRepository.forgetRom` matches the selected URI exactly. It waits for an
active scan, persists the retained list, updates the in-memory list and only
then reports success. Details close after success; a failed cache write leaves
the entry and reports an error. The operation does not query the selected file,
delete a document, change search directories or revoke URI permissions.

Cache writes use `AtomicFile`, with recovery through its reader. Persistence
uses the current list under the same lock so an older queued emission cannot
restore a forgotten entry. An identical already-persisted snapshot is not
written again. This also avoids a redundant API-28 replacement window found
while testing immediate readback. Cache invalidation deletes the complete
`AtomicFile` state and resets the saved-snapshot marker under the list lock;
pending backup/new files cannot restore an invalidated library. Queued writes
are ignored while the repository is invalidated. The scan remains conservative about SAF
errors; unrelated entries are not automatically pruned.

| Responsibility | Source |
| --- | --- |
| Exact-URI removal, scan serialization and cache persistence | [FileSystemRomsRepository.kt](../../../app/src/main/java/me/magnum/melonds/impl/FileSystemRomsRepository.kt) |
| Repository operation | [RomsRepository.kt](../../../app/src/main/java/me/magnum/melonds/domain/repositories/RomsRepository.kt) |
| Confirmation, result and return to library | [RomDetailsActivity.kt](../../../app/src/main/java/me/magnum/melonds/ui/romdetails/RomDetailsActivity.kt), [RomDetailsViewModel.kt](../../../app/src/main/java/me/magnum/melonds/ui/romdetails/RomDetailsViewModel.kt) |
| Details action and callback | [RomConfigUi.kt](../../../app/src/main/java/me/magnum/melonds/ui/romdetails/ui/RomConfigUi.kt), [RomDetailsScreen.kt](../../../app/src/main/java/me/magnum/melonds/ui/romdetails/ui/RomDetailsScreen.kt) |
| Regression tests | [FileSystemRomsRepositoryTest.kt](../../../app/src/test/java/me/magnum/melonds/impl/FileSystemRomsRepositoryTest.kt) |

## Verification

### Initial JVM evidence — 6 November 2026

The initial tested source snapshot is based on
`a2e7795ee5e5f56e5b3ac509fbe8d644e82ee883` with the nine changed implementation,
resource and test files listed above and `res/values/strings.xml` plus
`res/values-b+it/strings.xml`. Its `git diff --binary HEAD -- <those nine paths>`
SHA-256 was `5ab822bc48d4bf95b6e577b0df66637e73c8ef92fb7d34bacc4e186fb868a708`.
This identifies the uncommitted tested change, not a published APK or tag.

| Gate | Observed result |
| --- | --- |
| RED, before implementing removal | New API initially returned false: **13 tests ran, 3 failed**, all three new removal/serialization cases; 10 existing tests passed |
| Initial removal GREEN | **14 tests passed, 0 failures, 0 errors, 0 skipped**, JVM/Robolectric API 28; report timestamp `2026-11-06T11:11:53.681Z` |
| Invalidation RED | New pending-backup regression ran on API 28 and 35: **2 tests, 2 failures** before complete atomic-cache invalidation |
| GREEN, final source | **16 tests passed, 0 failures, 0 errors, 0 skipped**, including the two API-28/API-35 invalidation cases; report timestamp `2026-11-06T11:25:13.510Z` |
| Kotlin/resources | Compiled by the focused JVM task, including the new UI callbacks and EN/IT strings |
| Whitespace/diff | `git diff --check` passed |
| Full JVM/API suite | **355 tests passed**: 351 app and 4 API tests; zero failures, errors or skips |
| Release builds and lint | Public three-ABI and compatible legacy ARM64 APKs built; zero lint errors, 479 warnings |
| Signed binary | Public APK SHA-256 `3b00f81647875408343f1e5895ccb36f39bf3bd77748de6f1f40c376c47bd10b`; persistent UD signer `832a0223…950d86`, code 51, not debuggable or testOnly; v2 signature and 16 KiB ZIP/64-bit ELF alignment verified |
| Android UI and installation | Exact-candidate public 49→51 and legacy 50→51 updates, selected-entry removal and cold persistence passed within the dated Android coverage below |

The added tests execute the production repository with real temporary
files and resolver/provider calls. Only ROM parsing and settings-directory
discovery are isolated by the existing fixture:

- `forgettingOneRomPreservesOtherMetadataAndAllFiles`: removes one URI despite
  equal display titles, preserves the other record's configuration/history/
  `storageNameVersion`, reads unchanged ROM/save bytes, checks zero provider
  delete requests and repeats removal without affecting the retained record.
- `forgettingUnavailableRomDoesNotRequireProviderAccess`: removes the selected
  cached entry when metadata access is denied, preserving the other entry and
  checking the serialized result.
- `forgettingWaitsForActiveScanSoItsResultCannotRestoreTheRecord`: holds a real
  scan at its read boundary, starts removal, then releases the scan and checks
  the final in-memory and persisted lists.
- `failedForgetWriteKeepsTheOriginalLibrary`: makes the cache destination's
  parent a regular file, verifies failure and preserves the original list.
- `invalidationDoesNotRestoreAtomicBackupAndRescanPersistsSameLibrary`: leaves
  a pending atomic backup (and a new-file candidate on API 35), invalidates the
  cache while provider access is denied, checks that old records do not return,
  then rescans the same library twice and verifies its persisted content.

Run with Java 21 and the configured Android SDK:

```sh
sh ./gradlew :app:testGitHubNightlyDebugUnitTest \
  --tests me.magnum.melonds.impl.FileSystemRomsRepositoryTest \
  --no-daemon --max-workers=2
```

The standard report is
`app/build/test-results/testGitHubNightlyDebugUnitTest/TEST-me.magnum.melonds.impl.FileSystemRomsRepositoryTest.xml`.
These JVM checks alone do not prove touch/controller/dialog behavior on Android,
every SAF provider, physical storage failure recovery or a release APK update.
The initial JVM-only test run performed no app update, data reset or
physical-device cleanup. Its limitations remain; the later Android checks below
apply only to their exact candidates. Historical APK acceptance is not
transferred to the new source.

<a id="android-candidate-acceptance"></a>
### Android candidate acceptance — 6 November 2026

Both candidates use app production source
`bd546465abffb6e775b3b9f51bcf1f07224eec89` and core source
`06f3df985f67416368e0716174b7f06934a10229`; the core's native correction set
remains `ac5e6ba0fb795b7aa8a43fdefed62eed8a37eff8`. Build-only version-label,
package and compatible signing choices distinguish the public APK from the
legacy Thor APK. Documentation changes after the production commit do not
identify a different executable build.

| Candidate | Exact identity and offline checks |
| --- | --- |
| Standalone public UD APK | `app-gitHub-nightly-release.apk`, `me.magnum.melonds.nightly`, `2.1.10-UD GH (NIGHTLY)`, code 51; SHA-256 `3b00f81647875408343f1e5895ccb36f39bf3bd77748de6f1f40c376c47bd10b`; 20,768,263 bytes; persistent UD signing certificate `832a0223e29802245c0e9f8ca24c8358e7d21cfe0dbea347845fda751f950d86` |
| Compatible legacy Thor APK | `me.magnum.melonds.nightly.perf`, code 51, ARM64; SHA-256 `9ac59a64fb7fda285ec7875e82377e073d9a3860f6feabb89bf70d35c4295d18`; preserves the legacy signing identity and updates code 50 in place |
| Package/build checks | Release flags, v2 signature and ZIP 16 KiB alignment passed. The public APK contains ARM64, ARM32 and x86-64 frontends, min API 24/target API 37. All ARM64/x86-64 ELF LOAD alignments passed 16 KiB verification; the ARM32 frontend has measured 4 KiB alignment and no 16 KiB runtime acceptance claim. |

| Android check | Observed result and boundary |
| --- | --- |
| Public-package 49→51 update: API 35 ARM64 AVD, 16 KiB pages | Same package/signing identity, UID and first-install identity retained. All 3 library records, 15 logical database tables, settings, controller data, runtime permission choices and persisted URI grants were preserved across installation and first launch, with only the launch preferences described below allowed to change. |
| Public APK, copied raw-save cycle | One copied Italian raw save passed Continue, native SAVE and Continue in a distinct cold process. An independent bounded save audit passed 46 checks. This is single-fixture evidence, not universal save compatibility. |
| Public APK, native UI removal | The confirmation removed exactly 1 diagnostic entry from a 4-record library, retaining the other 3 records and their fields. Database/cheat rows, protected settings and URI grants were unchanged. The 3-record result survived cold restart without resurrecting the removed entry. |
| Physical AYN Thor, compatible legacy 50→51 update | Installation and launch retained all 16 library records, 15 logical database tables, UID, signing identity, settings, permissions and persisted URI grants. The only allowed launch preference was `last_version` 50→51. |
| Physical AYN Thor, native UI removal and cold restart | Exactly 4 selected obsolete entries were removed, 16→12. The retained active record and all 11 other records were unchanged. The exact 12-record result persisted after cold restart. No global library reset was used. |
| Physical external-file preservation | The final 21 checked external files retained the same paths and SHA-256 hashes as the pre-update inventory, including the retained game/save inputs. This is the inventoried set, not every file or provider on the device. |

On the public AVD, first launch displayed the inherited Nightly update prompt
and **LATER** was selected. The strict comparison retained its failure for the
changed `github_updates_nightly_next_check_date`. An explicit launch-only
comparison then accepted that one positive LONG reschedule: the old date was
expired, and the new date minus 86,400,000 milliseconds lay between the device
timestamps of the two snapshots. This matches
`GitHubNightlyUpdatesRepository.skipUpdate` → `scheduleNextUpdate`.
`github_updates_nightly_last_release_date` stayed unchanged; `last_version` 49→51
was the other permitted launch change. No general updater preference allowance
was used.

The save audit retains the initial strict byte-difference failure. Its bounded
pass explains named new-console, elapsed-time and roamer mutations using
specific source/field checks; it does not mask an arbitrary save region. The
later new-process cold Continue is separate runtime evidence, rather than an
inference from the save audit alone. No personal save contents or device
captures are part of this public report.

These Android runs cover the normal confirmation, persistence and cold-restart
paths on the named ARM64 AVD and physical device. Failure/invalidation coverage
remains the focused JVM/Robolectric tests above. Every SAF provider, persistent
storage faults, power-loss durability, ARM32/x86-64 runtime behavior, broad
game/device compatibility and DSi HOME/quota remain outside this acceptance.
The code 51 raw-save gameplay cycle ran on the copied public-AVD fixture; this
physical update did not repeat native SAVE or game Continue on the device's
active checkpoint. Earlier physical gameplay evidence retains its original
code 50 identity and limitations.

Upstream integration remains at `fa214944`; the six later app commits observed
at `feab12e5` are not integrated by this correction. Core code is unchanged by
the library-entry removal feature.
