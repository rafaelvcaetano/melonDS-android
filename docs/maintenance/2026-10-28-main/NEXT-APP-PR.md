# Unpublished Android PR draft

**Proposed title: Nightly update: easier setup, cheat search and Android fixes**

- Source: Umberto-DEV/melonDS-android main; target: upstream master.
- Compared with the official **Nightly Build**, version 2.0.1 (c42995c), updated
  12 September 2026. Its app/core revisions still match upstream on 29 October.
- [Local comparison and full inventory](../2026-10-29-nightly-comparison/README.md).
- Review copy only. Push and PR publication require the maintainer's approval.
  The GitHub document links below become available after the authorized push;
  check them and add exact CI/companion-PR links before publication.

---

This updates the current Nightly with easier setup, searchable cheats and fixes
for imports, saves, audio and controls. The 2.1.5 candidate has now been installed
as an update on the Thor, with existing games, saves, cheats and settings preserved.

### Everyday improvements

- **BIOS setup:** find and copy the required files through a guided flow, with
  separate DS/DSi results, clear errors and retry. Incomplete copies are not accepted.
- **Cheats:** search folders, codes and selector options; group recognized modifier
  families into choices. Finding a result does not enable it. Dialogs work better
  with large text and the keyboard.
- **Wi-Fi:** edit three firmware connection slots and DNS addresses, and toggle
  slots from the pause menu. This does not promise that external servers work.
- **Settings:** enable the existing backup/restore feature and refresh restored
  controller settings immediately.
- **Controls:** add hold-to-fast-forward, key combinations and optional fast-forward
  mute; improve sleep/lid handling. Optional clock sync is off by default.

### Reliability fixes

- Preserve the previous cheat database when XML/DAT import fails or is cancelled;
  stop blocked imports and allow a fresh attempt. DAT support already existed.
- Recover failed ROM scans, keep list snapshots stable, save the selected sort
  order, load icons independently and limit concurrent 7z decoding.
- Improve save-write errors/retry, microphone samples and session handling,
  audio resume, stuck inputs and display reconnects.
- Reject invalid DSiWare inputs, read complete metadata and release temporary JNI
  references. Fall back safely to Software if an accelerated renderer cannot start.
- Correct scaled filter offsets and LCD/scanline alignment; fill missing strings
  and repair CI setup while keeping signed Nightly publication upstream-only.

### Community work and issue coverage

- Search extends [#1677](https://github.com/rafaelvcaetano/melonDS-android/pull/1677)
  by takanuva15. Credits also remain for WailAbou (#1606/#1608), dysprosium (#1625),
  N3MI-DG (#1648), DLCSharp (#1589) and selected side fixes by corigne (#1666).
- Fixes #1678: temporary JNI references accumulating while listing DSiWare titles.
- Addresses #264 by enabling and repairing existing settings/layout backup.
- Audio #1644/#1635/#1602, microphone #1679/#1285, RTC #1652/#1604,
  library #1519/#1378, DSi #1651/#1099 and display #1663/#1641/#1619 remain
  **partially addressed**. Their untested or unimplemented parts are listed in the
  [coverage table](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-28-refinement/ISSUE-PR-COVERAGE.csv).
- Supersedes closed, unmerged #1682/#1681. Adapted community PRs were not merged
  or closed upstream by this work.

### Validation and limits

- Recorded candidate: **267 JVM tests**, no failures/errors/skips; lint has
  **0 errors and 474 warnings**.
- Android ARM64: **52 passes plus 3 opt-in skips** on API33/4KiB and
  API35/4KiB/16KiB; separate real WorkManager and six native targets also pass.
- Additional evidence covers real GLES failure/fallback, sanitizer regressions,
  native UI captures and independent review.
- Thor Android 13: 52 isolated ART passes plus 3 opt-in skips, a separate
  WorkManager pass and six native passes. The installed optimized build also
  passed copied raw-save/restart, dual-display and read-only cheat/BIOS UI checks.
- [Thor update, preservation checks and before/after measurements](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-29-thor-update/README.md).
- [Before/after tests, exact build identity and limits](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-29-nightly-comparison/VALIDATION.md).
- The physical samples are descriptive; they do not establish a startup, CPU,
  RAM, battery or GPU improvement. An intermittent black display seen before
  updating still lacks a controlled reproducer. Physical microphone/peripheral
  checks, manual TalkBack and slow-provider Activity rotation remain open.

### Core dependency

- Companion core: insert the new PR link after publication is authorized.
- Review core first, then pin the accepted upstream revision and restore the
  upstream submodule URL. Repeat relevant checks if the core changes.
- [Full Nightly comparison, commit/file inventories and integration notes](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-29-nightly-comparison/README.md).
