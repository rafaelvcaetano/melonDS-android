# Thor 2.1.8 update and data preservation

4 November 2026. This report records the authorized physical update of the
existing modified Nightly installation. It is separate from the
[public UD APK and Android emulator tests](../2026-11-04-ud-nightly/README.md).
The [machine-readable checks](CHECKS.json) identify the exact binaries and
accepted comparisons. Raw backups, device identifiers, document URIs and
personal screenshots remain private.

## Installed identity

| Field | Before | After |
| --- | --- | --- |
| Device | AYN Thor, Android 13/API 33, ARM64, 4 KiB pages | Same device |
| Package | `me.magnum.melonds.nightly.perf` | Same package |
| Version code | 47, version 2.1.6 | 49, `2.1.8-UD GH (NIGHTLY)` |
| APK SHA-256 | `2dc30348bc52d412b6bd8ac6e41774c012ad44e566e01011838b1160b62d05a0` | `68f31a9059fbd6866d8892ac014d5c17ea4fe673440eae799b7ac18c5f7ceca2` |
| Certificate SHA-256 | `41ad82cd4365c73195128975cf0b920699f8833093507957dc2d0f9d6bb0b4b1` | Same certificate |
| UID and first installation time | Recorded before installation | Unchanged |

The installed ARM64 APK is 10,996,321 bytes, release optimized with R8/resource
shrinking, not debuggable and not testOnly. Its executable app source is
`4809d38fd8dfe2745a1605377a5825516a6aec58`; core is
`2bb701ffd6b2cdd6260796eac5b1d4173a5e72fa` (native code unchanged from
`ac5e6ba0`). Signature, package, version, frontend ABI and flags were inspected
from the APK. The installed device APK was hashed and matched that candidate.

The public `.nightly` UD APK has a different package and certificate. It is
not the artifact installed on this legacy device. Its source distribution is
pinned by [the app release](https://github.com/Umberto-DEV/melonDS-android/releases/tag/ud-nightly-2.1.8),
with the [companion core checkpoint](https://github.com/Umberto-DEV/melonDS-android-lib/releases/tag/ud-nightly-2.1.8).
No upstream PR was submitted.

## Method and results

A temporary same-signer instrumentation helper captured credential-private
and device-protected storage before target Application/provider startup.
Every archive passed ZIP CRC, manifest, size and SHA-256 checks. A fresh
preupdate capture matched the earlier preparation capture; those original
captures were retained. Copies of SQLite files were checked independently of
the app, including schema, integrity, ordered rows and logical hashes.

The installation used an explicit device serial and an in-place update:

```sh
adb -s YOUR_SERIAL install -r --no-incremental melonDS-UD-2.1.8-thor-arm64.apk
```

No target uninstall, clear-data, downgrade flag, signing change or testOnly
bypass was used. The old installed APK matched its retained canonical copy.

| Check | After installation, before normal startup | After normal library startup |
| --- | --- | --- |
| Credential-private files | All 17 byte-identical | Protected contents retained; expected changes listed below |
| Room database | Integrity `ok`, schema 8, all 744,262 rows unchanged | Same logical hash/counts and unchanged database/WAL/SHM bytes |
| Cheat data | 709,658 cheats,30,341 folders,4,255 games unchanged | Same rows and values, including selections |
| Library | All 11 records unchanged | All original fields unchanged by unique document URI |
| Controller/layout/WFC/state screenshot | Byte-identical | Byte-identical |
| Persisted document permissions | All 3 unchanged | All 3 unchanged |
| Runtime permission choices/flags | Preserved by installation | All 4 unchanged |
| External originals | Verified backups before update | All 41 inventoried files rehashed unchanged |
| microSD NDS inventory | Recorded before update | Same paths; no new game or backup directory |

After startup the accepted differences are:

- `last_version` changes from 47 to 49.
- The ROM cache is reordered and explicitly records `storageNameVersion:0` for
  the 11 legacy entries. Matching by unique URI proves every original field,
  including configurations and play history, is unchanged. The repository
  identifies documents by URI; the frontend applies its own sorting. Version0
  preserves the legacy save/state filename fallback.
- Two derived update-check timestamps are initialized in the default
  preferences. The release date matches the retained legacy cache; the next
  check is one day after an instant inside the observed startup interval.
  The user's update-check choice and every other preference value are
  unchanged. This is cache initialization, not an acceptance test for installing
  updates from another signer.
- Two device-protected shader caches were cleared by installation and
  regenerated after startup. Two ART profile bookkeeping files changed.

The private comparators reject unexpected user preference changes, altered ROM
fields, duplicate document URIs, missing protected files, invalid manifests and
arbitrary timestamp changes. Original failed comparator reports and regression
source are retained privately. No production algorithm was replaced for these
comparisons.

Seven historical external paths were already absent before this campaign.
Their absence was recorded before installation; nothing was restored or deleted
at those paths. The41-file comparison is bounded to the inventoried inputs,
not an exhaustive certification of all device storage.

## Runtime evidence and limits

The actual installed app cold-started into its preserved library, confirmed by
its screen and resumed activity. Bounded app logs contained no fatal Java/JNI,
native signal or ANR markers. The four runtime permissions retained their
previous grant values and flags.

The UI hierarchy command returned an empty file despite exit0. A window-only
dump also omitted the focus header expected by the first harness. These were
capture limitations; the actual screen and activity state supplied the UI
proof. The empty owned transport file was recorded and removed.

The initial private comparator also rejected an empty device-protected
snapshot. Inspection showed that area previously held only the two shader
caches. The corrected harness requires a valid archive/root/manifest and keeps
credential-private storage nonempty; negative tests still reject loss of a
protected device-protected file. Subsequent refinements compare unordered
cache records by production identity and tightly validate the derived dates.
These are verification-harness corrections, not additional emulator bugs.

No personal game was launched or saved during physical acceptance. Continue,
SAVE, normal Exit and new-process cold Continue were tested on named copies
in the AVD, as documented in the paired report. Physical gameplay, all games,
old savestates, power-loss recovery and complete custom BIOS/NAND adoption
remain outside this physical acceptance. Preserved savestate bytes do not
certify that every old state will load under stricter validation.

Use fork downloads for future UD updates. The inherited in-app checker still
queries the original project; it does not distribute fork-signed APKs. The
[update guide](../../user-guide/UD-NIGHTLY.md#installation-and-data-preservation)
explains the distinct signers/packages. A retained older APK is evidence and
recovery material, not a tested in-place downgrade procedure.

## Cleanup and review

The temporary helper was removed after comparison. Six new transport ZIPs,
88,173,731 bytes total, were removed only after their complete Mac copies were
reverified; those protected backups remain local. No backup folder was added
to the microSD. Only owned UI transport files and reproducible verification
working copies were cleaned, with manifests before deletion. The two campaign
AVDs were already stopped; the shared ADB server was preserved. Coordination
with the separate ROM work confirmed it was not using Thor/ADB.

Submission drafts remain local and unpublished upstream. The current
[app overview](../../../README.md#whats-new) and
[core overview](https://github.com/Umberto-DEV/melonDS-android-lib#whats-new)
summarize features, fixes and credits.
Local corrections do not close upstream issues. The owner's physical gameplay
review and explicit approval remain required before any upstream PR is opened.
