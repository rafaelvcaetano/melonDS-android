# Thor RTC update 2.1.9

4 November 2026. The owner explicitly authorized this update after the
[upstream RTC alignment](../2026-11-04-upstream-alignment/README.md).
This is the modified Android fork built on upstream master `fa214944`,
with the existing fork features and forward-only RTC policy. It is not an
installation of the author's differently signed APK. The
[previous physical delivery](../2026-11-04-thor-update/README.md) remains
historical evidence for 2.1.8; its results are not relabeled for this APK.
Exact results are in [CHECKS.json](CHECKS.json). Private backups, document URIs,
device identifiers and personal screenshots are retained locally.

## Installed build

- Version: **2.1.9-UD GH (NIGHTLY), code 50**, updated in place from 2.1.8/49.
- Package: `me.magnum.melonds.nightly.perf`; ARM64, minimum API 24, target API 37.
- App source: `d8f18d0017b4a4d97690ccb783eb5b12838537c6`;
  core: `2bb701ffd6b2cdd6260796eac5b1d4173a5e72fa`.
- APK: 10,996,321 bytes; SHA-256
  `c66d46c5a53ae66f03515855b1fb70800d85baaad9dcd847d3224bf9af8736c1`.
- Certificate SHA-256:
  `41ad82cd4365c73195128975cf0b920699f8833093507957dc2d0f9d6bb0b4b1`,
  unchanged from the installed legacy package.
- Release build with R8 and resource shrinking; not debuggable and not testOnly.
  APK signature v2 and 16 KiB ZIP alignment verified. The device APK hash matches.

The main source change since 2.1.8 is the stopped-session resume guard described
in the alignment report. The existing RTC preference, default-off behavior and
30-second forward-only threshold remain. No database migration, save-format,
cheat-format or ROM-writing change was introduced by this integration.
Version 50 identifies the new build; it does not imply another security scan.

## Checks before installation

| Layer | Result |
| --- | --- |
| JVM/API | 345 app and 4 API tests passed; no failures, errors or skips |
| Release build and lint | Successful; no lint errors and 479 warnings |
| Native regression | Integration CI passed both Linux sanitizer jobs, including eight dispatch cases and six real-core RTC controls; version-only bump changes no native code |
| Android RTC | Six controls passed on each 4 KiB and 16 KiB ARM64/API35 AVD for the integrated native source; `ftruncate` warning retained in the alignment report |
| Exact optimized APK, 4 KiB AVD | 49-to50 in-place installation, homebrew launch, pause/resume, continued screen-color alternation and normal Exit passed |
| AVD data | UID, first installation time and all six protected private files retained byte-for-byte before normal startup; no populated Room database was present |
| CI integrity and diff | Dependency integrity and app/core diff checks passed |

The release task was `:app:assembleGitHubNightlyRelease`, with
`:app:lintGitHubNightlyRelease`. The two lintVital tasks were skipped by Gradle;
the full lint task executed successfully. The upstream-only signing/publishing
CI job is intentionally skipped on this fork. Local build configuration selected the existing
legacy package/signer, ARM64 and version name `2.1.9-UD`. The initial output was
rejected because a diagnostic ABI-injection property made it testOnly. The
production rebuild omitted that property. An old intermediate APK also retained
the rejected flag; the final artifact was taken from the production outputs
path and independently checked. Neither rejected output was installed.

The AVD test used a named working copy of the existing generated homebrew,
131,072 bytes, SHA-256
`e298b528668165047d8bb4d7dfa0df038ddb9a6d91289b6f7a84b55e8b2bd5f4`.
An initial whole-database comparator correctly refused the AVD's missing Room
database; that is a skip, not a populated-database PASS. Android35 reports
`appId` rather than `userId`; the identity parser was corrected using retained
before/after package dumps. The actual Thor database was verified separately.

## Physical update and preservation

Coordination confirmed Sacred Gold Plus was not using Thor/ADB. The app was
already stopped; no game session was interrupted. A temporary same-signer helper,
previously authorized by the owner, made fresh private and device-protected
snapshots before Application/provider initialization. ZIP CRC, manifest sizes,
member hashes and complete archive hashes were checked on the Mac. The Room
database was independently verified from copies, including ordered logical rows.

The original installed 2.1.8 APK matched its retained fallback before this command:

```sh
adb -s YOUR_SERIAL install -r --no-incremental melonDS-UD-2.1.9-thor-arm64.apk
```

No target uninstall, clear-data, downgrade, signing change or testOnly bypass
was used. The candidate was then read back by hash from the device. Comparison
ran both before normal startup and after cold-opening the preserved library.

| Protected state | Observed result |
| --- | --- |
| UID and first installation time | Unchanged |
| Room | Integrity ok, schema 8, all 744,262 logical rows unchanged |
| Cheats | 709,658 cheats, 30,341 folders and 4,255 games unchanged |
| Library | All 11 records preserved |
| Preferences, controller, layouts and WFC | Preserved; only expected `last_version`49-to50 change after startup |
| Document and runtime permissions | Same three persisted URI grants and four runtime choices/flags |
| Inventoried external originals | All 42 files rehashed unchanged after the update |
| App startup | Library resumed; bounded app logs contained no fatal Java/JNI, native-signal or ANR markers |

Two device-protected shader-cache files were removed by Android installation;
that area was empty in the post-install and post-library snapshots. Changes to
ART/profile bookkeeping were kept separate from protected user data. The
comparators found no unexpected protected changes and reject unrelated setting
changes or an incorrect version transition. No personal game was launched or
saved. The 42-file check is bounded to that inventory, not all device storage.
All 42 inventoried paths remained present and unchanged. This procedure created
no directory on the microSD; it did not repeat an exhaustive post-update
whole-storage inventory.

## Cleanup and handoff

The helper was removed. Eight owned transport ZIPs, 116,770,972 bytes, were
removed only after their complete Mac copies were reverified. Protected backups
remain local. The AVD helper and named working files were also removed; its
display configuration was restored and the owned AVD stopped. No shared ADB
server or unrelated process was stopped. Build intermediates totaling
830,865,328 bytes were removed after per-file manifests; the exact APK, R8
mapping, native symbols, test XML, lint reports and logs remain local. Available
space afterward was 14,704,640,000 bytes, about 13.7 GiB.

The owner was told the Thor could be disconnected after all physical checks and
helper removal. Physical gameplay, RTC events in individual games, multiplayer,
old savestates, all renderers and power-loss behavior remain separate acceptance.
No performance or power-saving improvement is claimed for this small integration.

The published UD 2.1.8 pre-release and its immutable assets remain unchanged;
2.1.9 is a local legacy-package delivery for this physical test. Promotion to
Latest and upstream PR publication remain subject to the owner's explicit approval.
