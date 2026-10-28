# Unpublished core PR draft

**Local draft. Publication requires the maintainer's explicit authorization.**

Source: `Umberto-DEV/melonDS-android-lib:main` →
`rafaelvcaetano/melonDS-android-lib:master`.
Verified core: `fd7582400bb7e35fe0d36dbb0c89b6766c1a8f2e`.
Refresh source/target revisions and insert the paired app/CI links before publication.

Title: Fix JIT invalidation and memory mappings, DS/DSi registers and Wi-Fi power saving

This corrects shared-core register, JIT and Wi-Fi behavior while retaining normal
resolver behavior and avoiding work for inactive renderer paths. Android frontend
BIOS/import/microphone/UI fixes and failed-renderer type handling belong to the
companion app proposal; the later refinement leaves this core revision unchanged.

## Changes and before/after behavior

| Area | Previous behavior | Result |
| --- | --- | --- |
| DS/DSi SCFG | Register initialization/access did not consistently follow the cartridge's console mode | Apply the corresponding DS/DSi initialization and access restrictions. |
| Wi-Fi power saving | Beacon countdown could power down the radio when automatic saving was disabled | Power down only when automatic power-saving mode is selected. |
| JIT literal handling | Untracked literals could be folded/registered and invalidation used an inappropriate mask | Fold only tracked literals on ARM64/x86_64 and use the actual write mask. |
| JIT destruction | Fast-memory teardown read ARM9 configuration after ARM9 destruction | Release mappings while ARM9 is still alive; keep backing memory through component teardown. |
| DTCM fast memory | RAM writes could alias DTCM and remapping could assert | Bound mappings to configured windows and physical offsets; symmetric map/unmap and invalidation of the old interval. Sub-page windows retain slow fallback. |
| Compute setup/edge work | Unchanged configuration and inactive edge pass still caused work | Skip unchanged configuration and avoid inactive edge data/upload/shader work. Remove a misleading cache-rejection diagnostic. |
| Optional DNS overrides | Frontend could not supply hostname answers to the built-in responder | Optional name→IPv4 table, empty by default; case/trailing-dot normalization and A-query behavior covered by packet tests. |

WFC firmware settings in the app are independent of the optional DNS override API.
Do not describe this table as a multiplayer compatibility fix.

## Evidence and limits

- The teardown defect was reproduced under strict UBSan. Its cause corresponds to
  [melonDS-emu/melonDS#2537](https://github.com/melonDS-emu/melonDS/issues/2537),
  reported by maximxlss and analyzed by RSDuck. The local implementation is not a
  copied upstream patch; the upstream issue remains open and is not automatically
  closed by a PR in this repository.
- Android ARM64 DTCM regressions isolate the alias using separate fast-memory and
  literal controls plus physical sentinels. Coverage includes 4/8/16/32 KiB windows,
  both 32 KiB mirrors and movement of the configured base. The corrected executable
  passes on Android 13/4KiB and Android 15/4KiB/16KiB in the paired campaign.
- Host JIT sanitizer and DNS packet checks are supporting evidence. The native
  core is real, but synthetic cartridge/frontend adapters are explicit. Linux
  x86_64 execution is not Android x86_64 execution; DNS checks are functional,
  and host leak detection is disabled in the retained harness.
- The previous exact-core CI run is [40696625134](https://github.com/Umberto-DEV/melonDS-android-lib/actions/runs/40696625134).
  Insert current paired CI links before publication. The app's 267 JVM/52 ART tests
  describe companion validation, not 267 tests of this core alone.
- No physical Thor acceptance, all-game JIT compatibility, lower energy use,
  faster renderer or better 6x image quality is inferred. Android game reports
  #579/#751 need their own scenarios. The current 2D-upscaling and GPU-visibility
  research is not included as an implemented optimization.

[Paired integration report and before/after ledger](../2026-10-28-refinement/INTEGRATION-REPORT.md).

## Integration and provenance

Companion Android proposal: insert URL only after explicit publication authorization;
source `Umberto-DEV/melonDS-android:main`, target upstream `master`.
Merge this core first. If acceptance changes the revision, update the app's gitlink
and submodule URL and rerun relevant checks before merging the app.

Preserve existing source attribution and the histories consolidated from the
previous proposal. This supersedes closed, unmerged lib #19/#18; it does not
represent their prior acceptance upstream. Original descriptions remain archived.
The larger audio buffer in lib #17 and the separate enable-tag/firmware-path
proposals lib #1/#3 have not been imported by this refinement.

Publication remains blocked until the maintainer authorizes it. No new PR,
branch, remote rewrite or automatic issue closure is performed by this draft.
