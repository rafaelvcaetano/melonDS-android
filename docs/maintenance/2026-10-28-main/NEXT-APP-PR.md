# Android PR draft — not published

**Title: Improve Android setup, controls and reliability**

Updated 3 November 2026. Source: `Umberto-DEV/melonDS-android:main`;
target: `rafaelvcaetano/melonDS-android:master`. Publication remains blocked
pending the coordinated review and explicit approval. Check final CI and document
URLs before publication; add a companion PR URL only after it exists.

---

This adds guided setup, cheat search and more controls to Android Nightly. It
protects existing saves and settings when an operation fails, fixes resource
lifetimes, and validates files, archives, native state and launch requests
before using them.

The scope includes the earlier Thor changes and the November fixes. The baseline
is official Nightly app `c42995ca` with core `431ab4bd`, rather than the last local
update. The app initially pinned core `65952aec`; the final paired commit pins
the new core correction `ac5e6ba0`. The final source pair is app `0018cddb` and
core `ac5e6ba0`. The candidate identity and local runtime checks are in the
second-campaign report.

## Features

- Guided BIOS/firmware setup with separate DS/DSi results, named errors and retry.
- Cheat search across folders, codes and selector options; grouped modifier
  choices, including the existing Sacred Gold Plus profile. Search does not
  enable a cheat. DAT support already existed.
- Three firmware Wi-Fi slots with DNS editing and pause-menu toggles.
- Settings/layout backup and restore, exposing the existing upstream manager.
- Hold-to-fast-forward, combination hotkeys, optional fast-forward mute,
  sleep/lid handling and optional forward-only clock sync, off by default.

See [features, original authors and test contracts](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-28-refinement/INTEGRATION-REPORT.md).

## Bug fixes

Each evidence link supplies the previous behavior, correction and test commands.
The [baseline and test ledger](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-29-nightly-comparison/VALIDATION.md) distinguishes inherited official-source defects from fixes to new
fork features. A source baseline check is not a run of the official APK.

| Area | Corrected behavior | Evidence and tests |
| --- | --- | --- |
| Cheat import | Preserve the old Room database on parse/write/cancel failure; handle fragmented DAT/XML; unblock owned reads on cancel and allow retry | [reproduction and Android checks](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-28-android-bugfixes/VALIDATION.md) |
| ROM library | Exit SCANNING after failure, serialize scans, keep emitted lists stable, persist the selected sort order, load icons independently and limit 7z decoding | [features, original authors and test contracts](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-28-refinement/INTEGRATION-REPORT.md) |
| Saves | Prefer non-truncating access, retain pending writes, retry/report failures and serialize whole-state operations | [save/native regression commands](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-28-main/VALIDATION.md) |
| Microphone/audio | Saturate amplified PCM; keep capture cursors with their session; serialize source/stream controls; handle failed stream-open and resume/mute policy | [reproduction and Android checks](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-28-android-bugfixes/VALIDATION.md) |
| DSiWare/JNI | Reject invalid imports and mismatched metadata; read complete TMD; correct cleanup; release title-list local references | [reproduction and Android checks](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-28-android-bugfixes/VALIDATION.md) |
| JNI URI leases | Release every acquired import/export URI string, including non-copy leases; stop on failed acquisition | [separate lease RED/GREEN](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-31-persistence-lifecycle/README.md) |
| BIOS and cheat dialogs | Reject incomplete staged setup copies, preserve retry state and keep actions usable with large text and keyboard | [production regression tests](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-28-refinement/VALIDATION.md) |
| Controls/lifecycle | Release interrupted touch/controller input; update stationary pointers; preserve lid pause deadlines and session/FPS ownership | [features, original authors and test contracts](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-28-refinement/INTEGRATION-REPORT.md) |
| Displays/rendering | Restore layouts on reconnect; follow the actual Software renderer after accelerated creation fails; correct scaled offsets and LCD/scanline alignment | [production regression tests](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-28-refinement/VALIDATION.md) |
| Resources/builds | Fill missing strings/formats, refresh restored controller settings and repair Android CI setup | [features, original authors and test contracts](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-28-refinement/INTEGRATION-REPORT.md) |

## Security corrections

The completed scan recorded **35 unique occurrences**: 15 app-only, 17 core-only
and 3 shared. This proposal covers 18 app/shared entries, and the companion core
covers 20. The three shared entries count once. All 35 have source corrections;
the per-entry evidence distinguishes dynamic RED/GREEN from source-backed cases.

| Area | Correction | Evidence and tests |
| --- | --- | --- |
| ROM/cache | Cap direct loads and extraction; enforce live disk quota; replace 32-bit URI hashes with SHA-256 cache keys | [per-occurrence correction and controls](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-remediation/FINDINGS.csv); [post-fix before/after and verification guide](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-remediation/ADVERSARIAL-REVIEW.md) |
| Savestates | Cap allocation, pass the real RCHV buffer length, bound save-flush spans and restore the previous state on semantic rejection | [security report and repeatable commands](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-remediation/README.md) |
| External launches | Reject malformed/null data; accept granted content URIs; use explicit shortcuts and reject serialized file authority | [post-fix before/after and verification guide](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-remediation/ADVERSARIAL-REVIEW.md) |
| Cheat parsing | Bound counts, code/text totals and XML expansion; reject DTD/external entities | [per-occurrence correction and controls](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-remediation/FINDINGS.csv); [post-fix before/after and verification guide](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-remediation/ADVERSARIAL-REVIEW.md) |
| Settings/controller/layout restore | Validate actual values/DTOs, exclude non-transferable authority and derived metadata, and recover interrupted AtomicFile writes before consumers | [post-fix before/after and verification guide](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-remediation/ADVERSARIAL-REVIEW.md) |
| Firmware/DSiWare | Validate one bounded immutable input, bound title strings and reject truncated content or unauthenticated metadata transport | [per-occurrence correction and controls](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-remediation/FINDINGS.csv) |
| RetroAchievements | Send sensitive login fields in POST bodies; exclude secrets from platform backup | [security report and repeatable commands](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-remediation/README.md) |
| CI dependencies | Pin 27 Action references and the Gradle distribution checksum, with an automated integrity policy | [security report and repeatable commands](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-remediation/README.md) |

DSiWare network metadata import now requires a configured trusted HTTPS source;
there is no default network source. The paired core also blocks slirp
private/loopback targets and changes LAN player serialization. These behavior
changes need compatibility review before upstream acceptance.

### Second independent campaign

Completed scan `ab26b107-97dd-4557-9c4b-2942e5df879d` added **66 occurrences**
without recounting the earlier 35. They normalize to 48 cause groups: 62 results
are corrected in repository code, two release findings have repository gates but
the required production environments are absent in the observed GitHub API
and tag protection remains unverified, one R4 path is not applicable
to Android, and one result is an informational reconciliation. Exact IDs,
severity, disposition and evidence remain in the canonical register.

| Area | Changed behavior | Evidence |
| --- | --- | --- |
| ROM, archives and exported launch | Bound compressed and decoded work, isolate provider-controlled names, bind cached identities to digest and revalidate staged copies | [second campaign, before/after and compatibility](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-followup/README.md) |
| NAND/SAF lifecycle | Bound guided copies/title enumeration, keep leases until work completes and fail exact I/O without adopting incomplete state | [per-occurrence dispositions](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-remediation/FINDINGS.csv) |
| Cheat, camera and achievements | Add document/pixel/response budgets, memory-region bounds and deterministic JNI/resource release | [second-campaign verification](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-followup/README.md#prove-primadopo-e-verifiche-finali) |
| Savestate and native bridge | Restore the pre-load state on semantic rejection; reject invalid dimensions, queue state and native indices before use | [cause reconciliation and limits](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-followup/README.md#ricerca-smentite-e-correzioni) |
| Update and release trust | Disable production GitHub polling when updates are disabled; verify Gradle artifacts and gate production release provenance; production environment protection remains a publication prerequisite | [release control checks and limits](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-followup/README.md#ricerca-smentite-e-correzioni) |

Post-scan verification also corrected three paired-core boundaries without
recounting them as scan occurrences: Action Replay now carries one frame budget
across repeated IRQs and resumes the deferred cheat without changing `Enabled`;
G.711 spans are intersected with the ARM9 NWRAM aperture; and the Linux x86-64
Thumb JIT no longer folds literals without an invalidation owner. See the
[extra-scan review and RED/GREEN evidence](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-followup/README.md#revisione-successiva-al-primo-push)
and the [source-pinned finding detail](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-followup/FINDING-DETAILS.md#causa-extra-scan-non-contata).

The Security results UI may still label entries Open. This draft reports the
local technical disposition only; it does not claim an upstream issue closure
or a published security advisory.
The [101-entry finding detail](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-followup/FINDING-DETAILS.md)
links every scan occurrence to its attack path, validation, counterevidence,
patch and remaining proof limits.

## Validation and remaining scope

- Current delivery: [2.1.8/49 identity, Android-on-Mac checks and conservative AVD update](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-followup/README.md).
- Final source pair: app `0018cddb`, core/gitlink `ac5e6ba0`.
  [App CI run 41704565464](https://github.com/Umberto-DEV/melonDS-android/actions/runs/41704565464)
  is green for JVM tests, lint/build and Linux native checks on x86-64 and
  ARM64; [core CI run 41704485288](https://github.com/Umberto-DEV/melonDS-android-lib/actions/runs/41704485288)
  is green.
- Final optimized update candidate: 2.1.8/49, SHA-256
  `195b5748fc59bac74eadee148f0a1a7b440e47b51dcc5f70fdcd384e855932cb`.
  Its `.nightly.perf` package and matching certificate update the local 2.1.7/48
  candidate. The standard Nightly source variant uses `.nightly`; this APK is
  not an in-place update for a differently identified upstream Nightly APK.
- Final second-campaign checks include host sanitizers, JVM/API, optimized APK
  build and lint, 11 NDK targets on ARM64 4/16 KiB, 55 default ART/provider cases
  (52 pass, three opt-in skips) plus the separately passing WorkManager case,
  and an in-place 2.1.7→2.1.8 AVD update preserving
  synthetic Room/cheat/layout/preferences and persisted SAF permission.
- The exact optimized APK also booted direct/ZIP homebrew and an authorized
  copied SGP 1.2.5 IT ROM/save on the 16 KiB AVD. The copied raw completed
  Continue, in-game SAVE, a new-process cold Continue and normal Exit; source
  ROM/save hashes remained unchanged. [Input identities and limits](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-followup/README.md#aggiornamento-conservativo-sullavd).
- Recorded security source pair: app `a77ccaaa`, core `65952aec`; 312 JVM tests,
  163 focused host ASan/UBSan controls, core host build, DNS/JNI suites, debug
  APK build and lint. Exact commands and limits: [security report and repeatable commands](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-remediation/README.md).
- [earlier Thor 2.1.6/47 acceptance](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-01-thor-update/README.md): in-place update, preserved data, ten native targets and one copied
  SGP save through Continue/SAVE/cold Continue. That APK predates the new fixes.

Host, JVM, NDK component execution, ART and physical Thor results are separate.
Thor remains on the documented 2.1.6/47 installation; this draft does not
authorize or claim a physical 2.1.8 installation.
No all-game, zero-bug, FPS, latency, memory or battery improvement is claimed.
Android 9 black-screen #1665, DSi HOME/quota, physical microphone/driver behavior
and device-specific audio/display reports remain open or partial. See [canonical issue/PR register](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-28-refinement/ISSUE-PR-COVERAGE.csv).

## Issue scope and review build

No upstream issue is claimed closed by this draft. The precise #1678 JNI cause
is corrected locally; #264 is covered by exposing the existing configuration
backup manager. Both issues remained open at the 3 November API check and are
candidates for closing wording only after final acceptance. #1665, #1651/#1099,
hardware microphone/casting and game-specific reports remain partial or open.
Contributor PRs are credited below; adaptation does not close their PRs.

The [Thor update and PR handoff](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-thor-2.1.8/README.md)
records the exact 2.1.8/49 local APK, backup/update acceptance procedure and
publication gates. It has not been installed on the Thor. There is no published
2.1.8 fork Nightly; CI provides a separate debug test artifact, which must not
be substituted for this local update. The exact-final-APK private bytewise
post-update comparison remains a proof gap; the full AVD data comparison used
an earlier candidate, as the report explains. A public review APK can be added
after physical acceptance and explicit publication approval.

## Credits and integration

Preserve work adapted from takanuva15 (#1677), WailAbou (#1606/#1608), dysprosium
(#1625), N3MI-DG (#1648), DLCSharp (#1589) and selected fixes by corigne (#1666).
The existing backup manager and DAT support retain their original attribution.

The precise JNI local-reference cause addresses
[#1678](https://github.com/rafaelvcaetano/melonDS-android/issues/1678);
backup/restore addresses [#264](https://github.com/rafaelvcaetano/melonDS-android/issues/264).
The issue register preserves partial coverage; there are no blanket closing keywords.
This replaces app #1681/#1682, closed without merge. Review core first, then pin
its accepted revision and upstream URL. No replacement companion PR exists yet.
The report and finding-detail URLs resolve on the fork's `main`. Raw private
runtime evidence and the optimized APK remain local. Recheck links and current
upstream revisions before separately authorized publication.
