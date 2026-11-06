# Comparison with the official Nightly Build

**3 November follow-up:** upstream app/core master and the rolling Nightly asset
still match the baseline below (checked again through GitHub REST). The frozen
October file/commit counts are historical. Use the
[current reconciliation, impact assessment and 2.1.7 delivery](../2026-11-03-nightly-2.1.7/README.md)
with the canonical app/core drafts for the complete later scope. Do not read
this October snapshot as a list of only the latest security fixes.

Checked on **29 October 2026**. This compares the complete fork with the latest
published official Nightly, not only the fixes from the last few days.

## Exact starting point

| Item | Official release |
| --- | --- |
| Release | [Nightly Build](https://github.com/rafaelvcaetano/melonDS-android/releases/tag/nightly-release), tag `nightly-release` |
| Latest APK update | 12 September 2026, 15:29:51 UTC |
| APK version, read from the downloaded file | `2.0.1 (c42995c) GH (NIGHTLY)`, version code 41 |
| App commit | [`c42995caff0144f37ad9791dc9bc96d82e8a6f65`](https://github.com/rafaelvcaetano/melonDS-android/commit/c42995caff0144f37ad9791dc9bc96d82e8a6f65) |
| Core pinned by that app | [`431ab4bd0003c4356e25fce89640ae1004579b9b`](https://github.com/rafaelvcaetano/melonDS-android-lib/commit/431ab4bd0003c4356e25fce89640ae1004579b9b) |
| Publishing workflow | [34701935940](https://github.com/rafaelvcaetano/melonDS-android/actions/runs/34701935940), successful push build of the same app commit |
| APK SHA-256 | `addc921e5ea539841b0de87b8cb7e00515a9fcce32191b5cdf7fdcb08e4f1b44` |

- The downloaded APK hash matches GitHub's asset digest.
- The annotated tag object `76348643d042dc4d0712843527101490741f5321`
  resolves to the app commit above. The core SHA was read from that exact tree.
- Both upstream `master` branches still point to these commits at this check.
  Consequently, the Nightly comparison and the prospective PR comparison have
  the same base today. Recheck both before publication: the Nightly tag can move.
- The release page was originally published in 2023 and is updated in place.
  Its old `published_at` value does not date the current APK.
- [Machine-readable source evidence](BASELINE.json).

## Build identities must remain distinct

| Build | Identity and scope |
| --- | --- |
| Official Nightly | `me.magnum.melonds.nightly`; release APK; frontend libraries for ARM64, ARMv7 and x86_64. |
| Earlier Thor installation | `me.magnum.melonds.nightly.perf`; `2.1.4 GH (NIGHTLY) (PROFILING)`; SHA-256 `198505e4f50d9eafa2dbc3f941fcec83f0b747e23c95cb00efdb57859ce8afaa`; frontend ARM64. Exact source commit is not attested. |
| Locally verified candidate | `me.magnum.melonds.nightly.dev`; `2.1.4 GH (NIGHTLY)`; functional app `89053f0f`, core `fd758240`; APK SHA-256 `72c3e4363f74da95e9e02df44fa3cba523ddf51ca2216e6992d220682926ec1b`; debug frontend ARM64. |
| Subsequent installed update | `me.magnum.melonds.nightly.perf`; optimized, non-debuggable 2.1.5/versionCode46; APK SHA-256 `0ecc829c6510aa4422902a7b415916a03d1052562bc7530d9edbdfefdb2766aa`. [Source identity, preserved data and physical checks](../2026-10-29-thor-update/README.md). |

- The maintainer used the earlier modified build on the Thor for about a month.
  That is historical feedback, not physical acceptance of the newer candidate.
- The subsequent physical update compares that modified 2.1.4 with 2.1.5.
  It is separate from an official-release runtime comparison; the source diff
  in this document continues to use the pinned official Nightly.
- The later Sacred Gold Plus tests used the acquired `.perf` APK on Android 13,
  Software 1x. They do not validate the new `.dev` APK or Compute 6x.
- Both local APKs contain third-party libraries for other ABIs, but their
  **melonDS frontend is ARM64 only**. The `native-code` manifest summary alone
  would overstate their emulator ABI coverage.
- APK byte sizes are recorded for identity only. Different optimization,
  symbols and ABI contents make a size/performance comparison misleading.

## What the app changes

| Area | Official Nightly | Our change | Classification |
| --- | --- | --- | --- |
| BIOS setup | Folder selection and existing validation | Guided recognition/copying, separate DS/DSi results, actionable errors and retry | New guided flow, with fixes to our new flow |
| Settings backup | Implementation already present but disabled by a feature flag | Enable backup/restore and refresh cached controller settings after restore | Expose and repair existing functionality |
| Wi-Fi settings | Firmware support exists; no equivalent WFC editor | Edit three connection slots/DNS and toggle slots from the pause menu; DS/DSi persistence | New UI and firmware editing |
| Cheat search | No equivalent search interface | Search folders, codes and generated options; highlight and navigate without enabling | Adaptation of PR #1677, extended locally |
| Cheat families | Individual codes | Recognize modifier families, present choices/exclusions, preserve the optional Sacred Gold profile's L+R toggle | New selector layer; no bundled ROM/save/BIOS |
| Cheat import | XML/DAT support already exists; deletion starts before import finishes | Complete reads, strict malformed-input handling, transactional replacement, bounded buffering and cancellation | Fix existing imports; DAT itself is not new |
| Audio/microphone | Unsafe amplified conversion, shared read cursor and fragile stream lifecycle | Saturating samples, session-owned cursor, serialized controls, safer open/resume and optional fast-forward mute | Fixes plus an optional feature |
| Saves and native bridge | Risky file modes, incomplete final flush and weak error handling | Prefer non-truncating access, stage pending writes, retry/report errors, serialize whole-state operations and bound JNI references | Reliability changes; not universal SAF atomicity |
| ROM library | Icons share scan work; failed scans can leave SCANNING; emitted list is mutable | Independent icon work, bounded 7z decoding, serialized scans, stable lists, error recovery and correct sort persistence | Library fixes; normal startup speed still unmeasured |
| External ROM launch | Existing URI handling | Adapted document matching/permissions and file-name fallback | Targeted adaptation; scoped-storage limits remain |
| Controls | Existing touch/controller support | Release stuck inputs, handle pointer transitions, add combinations and hold-to-fast-forward | Fixes and additional controls |
| Sleep and clock | Existing pause/lid/RTC support | Preserve lid-close pause deadlines and offer forward-only clock sync, off by default | Targeted behavior changes |
| Display and filters | Existing external displays and accelerated renderers | Restore layouts after disconnect/reconnect, scale filter offsets correctly, retain native LCD/scanline grid | Fixes; no new all-element upscaler |
| Renderer failure | Null accelerated factory falls back in core, but frontend still uses an accelerated cast | Track the actual Software renderer for settings/frames/screenshots; preserve requested preference | Fix present in the official source too |
| DSiWare | Existing manager and downloads | Reject bad inputs, read complete TMD, correct cleanup and local-reference lifetime | Fixes; logical quota/HOME Menu remain outside scope |
| App lifecycle | Existing sessions, updater and FPS overlay | Session-scope cleanup, controller-cache warming, FPS constraints and compatible download-receiver registration | Targeted reliability/compatibility changes |
| Resources and builds | Existing locales, SDK 37 and NDK 28 | Fill missing strings/formats, improve new dialogs, add tests/profiling/sanitizer options and repair CI setup | Supporting changes; SDK/NDK upgrades are not claimed |

The new selector's large-text/keyboard fixes and the guided-copy staging fixes
repair features introduced by this fork. They must not be described as old
official Nightly screens that already had those defects. The linked test ledger
also distinguishes the source baseline used for each regression.

## What the core changes

| Area | Change from the Nightly's pinned core |
| --- | --- |
| DS/DSi registers | Apply console-mode initialization and access rules consistently. |
| Wi-Fi | Stop automatic transceiver shutdown when automatic power saving is disabled. |
| JIT literals | Fold only tracked literals and use the actual write mask for invalidation, including ARM64/x86_64 guards. |
| JIT teardown | Release fast-memory mappings while ARM9 still exists. |
| DTCM mappings | Prevent RAM/DTCM aliasing; correct window sizes, offsets, remapping and invalidation. |
| Compute renderer | Skip rebuilding unchanged renderer settings. |
| OpenGL renderer | Avoid generating/uploading unused edge indices and compiling shaders for the inactive edge pass. |
| Diagnostics | Replace the false shader-cache rejection error with an accurate compile message. |
| DNS extension | Optional hostname-to-IPv4 override API, empty by default. Independent of the app's WFC editor. |
| CI | Add ARM64/x86_64 sanitized core build verification. |

This does not introduce core 1.1, OpenGL/Compute support or high-resolution 3D:
those were already in the official base. No OpenCL backend, new 2D upscaler,
audio rate-matching patch or measured GPU/energy gain is included.

## Complete source and commit differences

The inventory is frozen at app `f887228d7b4a23ec35db9916f0c75c27960119b2`
and core `fd7582400bb7e35fe0d36dbb0c89b6766c1a8f2e`. The app's functional
source is unchanged from the verified `89053f0f` candidate. The comparison
documents added afterwards are not included in these frozen totals.

| App group | Changed files | Added lines | Removed lines |
| --- | ---: | ---: | ---: |
| Production code | 134 | 6,597 | 837 |
| Resources | 34 | 1,553 | 16 |
| Tests and tools | 117 | 12,614 | 0 |
| Documentation and evidence | 60 | 34,225 | 0 |
| Build and configuration | 11 | 235 | 46 |
| **Total** | **356** | **55,224** | **899** |

- Core: **17 files**, +377/−175 lines; 16 production files (+342/−175)
  and one CI file (+35). Binary documentation files are counted as files, not lines.
- App history: **141 commits ahead, 0 behind**; 30 documentation-only commits,
  4 merges and 107 code/test/build commits. Core: **13 ahead, 0 behind**, including
  one merge. These are history counts, not feature or bug counts.
- Two exact repeated-patch pairs exist in each history. They came from earlier
  integration paths; the final tree contains one resulting implementation.
  Stable patch IDs identify exact equivalents only, not every semantic overlap.
- App: [all files](app-files.csv), [all commits and equivalent patches](app-commits.csv).
- Core: [all files](core-files.csv), [all commits and equivalent patches](core-commits.csv).
- [Totals, pinned revisions and diff hashes](DIFF-SUMMARY.json).

Reproduce the tree comparison with `git diff c42995ca f887228d` and
`git -C melonDS-android-lib diff 431ab4bd fd758240`. Use `git log --reverse
--topo-order BASE..HEAD` for history and `git patch-id --stable` for exact patch
equivalence. Because each base is an ancestor of the snapshot, the corresponding
two-dot and PR three-dot file diffs are equal. No rebase or history rewrite was done.

## Issues, credits and PR preparation

- [Existing per-item coverage](../2026-10-28-refinement/ISSUE-PR-COVERAGE.csv)
  contains 48 issue/PR entries and their limits. Its broad snapshot is dated
  28 October; the six key references rechecked on 29 October are in BASELINE.json.
- **#1678** remains open: its JNI reference defect is addressed and is the only
  automatic `Fixes` reference in the app draft.
- **#264** remains open: exposing backup/restore and refreshing controller data
  addresses the requested behavior. Preserve upstream authorship of the manager.
- Audio, microphone, RTC, 7z, DSi quota and display reports are partial; no blanket
  closure. The original core teardown report **melonDS-emu/melonDS#2537** remains open.
- Adaptations retain credits for #1677, #1606/#1608, #1625, #1648, #1589 and selected
  parts of #1666. Audio #1676/lib #17 and the full process-death recovery are not imported.
- Previous app #1682/#1681 and lib #19/#18 were closed without merge. No new issue
  or PR is closed or published by this comparison.
- The current [app overview](../../../README.md#whats-new) and
  [core overview](https://github.com/Umberto-DEV/melonDS-android-lib#whats-new)
  summarize features, fixes and contributor attribution. Submission drafts remain local.

Review the core first, then the app. Before publishing:

1. Recheck the rolling Nightly and both upstream targets; repeat the comparison if they move.
2. Obtain maintainer approval for push and PR publication. Keep working on `main`.
3. Run CI for the actual submitted revision; insert the resulting CI and companion links.
4. After core acceptance, restore the upstream submodule URL and pin its accepted SHA.
   The current `.gitmodules` deliberately points to the fork for local verification.
5. Agree the upstream version number: `2.1.4`/45 is our fork's value, not a new official release.
6. Review the profiling build and game-specific cheat profile as explicit additions.
7. Review the full documentation/test scope: a PR from `main` includes it all.
   A short PR body does not remove these files. Keep useful evidence linked and avoid
   presenting the 55,224 added lines as emulator code. No historical evidence was deleted here.

New commit text and drafts contain no assistant signatures. Previously published
history was preserved; any later cleanup of historical messages needs separate
authorization and must preserve human contributor credit.

## Validation and next device session

- [Before/after evidence and exact test boundaries](VALIDATION.md).
- [Detailed implementation ledger](../2026-10-28-refinement/INTEGRATION-REPORT.md).
- [Repeatable Thor comparison plan](THOR-COMPARISON.md).
- No functional source, installed app, user save or device setting was changed
  by this comparison. No new runtime or performance result is claimed.
