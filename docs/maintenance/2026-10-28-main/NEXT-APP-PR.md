# Android PR draft — not published

**Title: Improve Android setup, Wi-Fi settings and cheat search**

Source: `Umberto-DEV/melonDS-android:main` → `rafaelvcaetano/melonDS-android:master`.
Updated 4 November 2026. Publish only after the owner's explicit approval.

Submission scope: this is the short fork review summary. Before opening an upstream PR, narrow its scope to one feature or fix, following the [maintainer request](https://github.com/rafaelvcaetano/melonDS-android/pull/1672#issuecomment-5637055233). No upstream PR is submitted here.

---

This makes Android Nightly easier to set up and adds controls for cheats,
firmware Wi-Fi settings and gameplay. It also improves how the app handles
failed saves, imports and file access. The comparison starts from official
Nightly `c42995ca` and its core `431ab4bd`.

- Add guided BIOS/firmware setup with separate DS and DSi results, clear errors
  and retry. Incomplete setup files are rejected before use.
- Add cheat search across folders, codes and selector options, plus grouped
  choices. Searching does not enable a cheat. DAT support already existed.
- Expose three firmware Wi-Fi connection slots, DNS editing and pause-menu
  toggles. This does not guarantee that a particular game or server connects.
- Expose the existing settings/layout backup manager; add hold-to-fast-forward,
  combination hotkeys, optional fast-forward mute and sleep/lid handling.
- Preserve the previous cheat database when an import fails or is cancelled;
  retain pending save writes after an I/O failure and allow retry.
- Bound ROM/archive, cheat, firmware and saved-state input; release JNI
  references and other resources; reject invalid saved states while restoring
  the previous state.

The [feature and fix guide](https://github.com/Umberto-DEV/melonDS-android/blob/main/README.md)
links the detailed changes. The [individual finding records](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-followup/FINDING-DETAILS.md)
explain each original failure, correction, test and remaining limit. They
separate individual reproductions from source review and broader test suites.

Validation includes JVM/API tests, lint, Android builds, native sanitizer
checks, ARM64 Android execution with 4 KiB and 16 KiB pages, ART/provider tests,
and copied-ROM Continue/SAVE/cold Continue. These results belong to the APKs
identified in the [validation report](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-followup/README.md).
The signed [UD Nightly review build](https://github.com/Umberto-DEV/melonDS-android/releases/tag/ud-nightly-2.1.8)
has separate [APK identity and acceptance results](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-04-ud-nightly/README.md),
including 349 JVM/API tests and actual optimized-release Android checks.
The app pins the companion core; review core first, then use its accepted
upstream revision. A source patch cannot supply the author's signing key.

The exact JNI cause in [#1678](https://github.com/rafaelvcaetano/melonDS-android/issues/1678)
is corrected locally, and backup/restore addresses [#264](https://github.com/rafaelvcaetano/melonDS-android/issues/264).
Both remain open upstream. Android 9 #1665, DSi HOME/quota, hardware-specific
reports and other partial cases remain in the [issue coverage register](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-28-refinement/ISSUE-PR-COVERAGE.csv).
This replaces #1681/#1682, closed without merge; it does not close partial cases.

Credits: takanuva15 (#1677), WailAbou (#1606/#1608), dysprosium (#1625),
N3MI-DG (#1648), DLCSharp (#1589), and selected fixes from corigne (#1666).
Existing backup-manager and DAT authorship is retained.
