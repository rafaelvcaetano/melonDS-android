# 2.1.8 package and update compatibility

This reference records the optimized 2.1.8/49 candidate prepared on 3 November
2026. Its validation belongs to that exact APK and source pair. Current
user-facing features and update guidance are in the [UD Nightly guide](../../user-guide/UD-NIGHTLY.md).

## Recorded candidate

| Field | Verified value |
| --- | --- |
| Package | `me.magnum.melonds.nightly.perf` |
| Version | `2.1.8 GH (NIGHTLY) (PROFILING)` / versionCode49 |
| APK SHA-256 | `195b5748fc59bac74eadee148f0a1a7b440e47b51dcc5f70fdcd384e855932cb` |
| Bytes | 85,676,746 |
| Certificate SHA-256 | `41ad82cd4365c73195128975cf0b920699f8833093507957dc2d0f9d6bb0b4b1` |
| App/core source | app `0018cddb04278f2bca5054804de501a459eb1de9`; core `ac5e6ba0fb795b7aa8a43fdefed62eed8a37eff8` |
| Build | ARM64 emulator frontend, optimized RelWithDebInfo; not debuggable or testOnly |
| Minimum API | 24 |

The certificate retains the earlier local Android Debug identity; the build
itself is not debuggable. That identity differs from the original author's
release certificate and from a separately signed UD release.

## Compatibility boundary

The [last documented physical update](../2026-11-01-thor-update/README.md)
installed 2.1.6/47 with the same `.nightly.perf` package and certificate. That
provides a same-package, same-signer update path to this candidate; the actual
installed version and data generation must still be verified before an update.
A standard `.nightly` package is a separate installation identity.

The inspected official c42995c APK uses `me.magnum.melonds.nightly`, versionCode41,
and certificate SHA-256
`0493bb8749289f0304cc868489dd68b930c19ccc4dce2da876bfe6b9bdbabebc`.
A fork-signed APK cannot replace that official installation in place. A source
patch lets the author review the changes and build an official update with
their own signing key. Changing package names does not move private databases
or persisted document permissions; cross-package export/import is a separate
migration requiring its own checks.

These package suffixes identify installations, not different ROM or raw-save
formats. Sharing source implementations does not prove that every historical
savestate or third-party provider behaves identically.

## Recorded acceptance and limits

The [second-campaign report](../2026-11-03-security-followup/README.md) records
JVM/API, lint/build, host sanitizer, Android-native ARM64 4/16KiB, ART/provider,
WorkManager and optimized-APK runtime evidence. This exact APK opened direct/ZIP
homebrew and completed copied SGP 1.2.5 IT Continue/SAVE/cold Continue on the
16KiB AVD. The original ROM and save hashes were unchanged.

A full synthetic-data comparison exists for an earlier 2.1.8 candidate update.
For the exact final APK, UID, first-install time and runtime were checked, but
a private byte-for-byte reread was unavailable. The 47-to-49 physical migration
was not executed by this preparation report. Later release and physical-device
results must identify their own APK/source pair and retain these limits.
