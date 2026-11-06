# Remove a single ROM entry from the library

6 November 2026. The next UD Nightly release is being prepared as **2.1.10,
version code 51**. The source correction, JVM suite, release builds and lint
have passed. Android UI/update checks remain separate below.

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

The tested source snapshot is based on
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
| Full JVM/API suite | 351 app and 4 API tests passed; zero failures, errors or skips |
| Release builds and lint | Public three-ABI and compatible legacy ARM64 APKs built; zero lint errors, 479 existing warnings |
| Signed binary | Public APK SHA-256 `3b00f81647875408343f1e5895ccb36f39bf3bd77748de6f1f40c376c47bd10b`; persistent UD signer `832a0223…950d86`, code 51, not debuggable or testOnly; v2 signature and 16 KiB ZIP/64-bit ELF alignment verified |
| Android UI and installation | Pending for these exact new APKs |

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
These checks do not prove touch/controller/dialog behavior on Android, every
SAF provider, physical storage failure recovery or a release APK update. No
app update, data reset or physical-device cleanup was performed by this change's
test run. Historical APK acceptance is not transferred to the new source.

Upstream integration remains at `fa214944`; the six later app commits observed
at `feab12e5` are not integrated by this correction. Core code is unchanged by
the library-entry removal feature.
