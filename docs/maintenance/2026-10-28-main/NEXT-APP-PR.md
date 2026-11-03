# Unpublished Android PR draft

**Proposed title: Improve Nightly setup, cheats and Android reliability**

- Source: Umberto-DEV/melonDS-android main; target: upstream master.
- The 2 November core-only Thumb/DTCM correction requires an eventual gitlink
  update after its core commit; it adds no frontend source claim. See the
  [repository campaign](../2026-11-02-repository-campaign/README.md).
- Compared with the official **Nightly Build**, version 2.0.1 (c42995c), updated
  12 September 2026, as captured in the 29 October comparison.
- Local delivery: modified Nightly **2.1.6 / 47**, following the documented
  Thor 2.1.5 / 46. Installed in place on 1 November with data preservation checks.
- [Final candidate identity and checks](../2026-10-31-nightly-2.1.6/README.md).
- [Local comparison and full inventory](../2026-10-29-nightly-comparison/README.md).
- Review copy only. Local `main` push was authorized on 3 November; upstream PR publication remains blocked pending a later explicit instruction.
  The GitHub document links below become available after the authorized push;
  check them and add exact CI/companion-PR links before publication.

---

This updates the current Nightly with easier setup, searchable cheats and fixes
for imports, saves, audio and controls. Failed cheat imports retain the previous
database, and affected resource lifecycles have focused regression coverage. This is
the existing modified fork; the pending update preserves its features and credits.

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
- Release every acquired URI string in DSiWare save import/export, including
  `isCopy=false`; stop when acquisition fails. This is separate from the title
  list's local-reference correction.
- Correct scaled filter offsets and LCD/scanline alignment; fill missing strings
  and repair CI setup while keeping signed Nightly publication upstream-only.

### Community work and issue coverage

- Search extends [#1677](https://github.com/rafaelvcaetano/melonDS-android/pull/1677)
  by takanuva15. Credits remain for WailAbou
  ([#1606](https://github.com/rafaelvcaetano/melonDS-android/pull/1606),
  [#1608](https://github.com/rafaelvcaetano/melonDS-android/pull/1608)), dysprosium
  ([#1625](https://github.com/rafaelvcaetano/melonDS-android/pull/1625)), N3MI-DG
  ([#1648](https://github.com/rafaelvcaetano/melonDS-android/pull/1648)), DLCSharp
  ([#1589](https://github.com/rafaelvcaetano/melonDS-android/pull/1589)) and selected
  side fixes by corigne ([#1666](https://github.com/rafaelvcaetano/melonDS-android/pull/1666)).
- Fixes #1678: temporary JNI references accumulating while listing DSiWare titles
  ([issue](https://github.com/rafaelvcaetano/melonDS-android/issues/1678)).
- Addresses [#264](https://github.com/rafaelvcaetano/melonDS-android/issues/264)
  by enabling and repairing existing settings/layout backup.
- Audio [#1644](https://github.com/rafaelvcaetano/melonDS-android/issues/1644)/
  [#1635](https://github.com/rafaelvcaetano/melonDS-android/issues/1635)/
  [#1602](https://github.com/rafaelvcaetano/melonDS-android/issues/1602), microphone
  [#1679](https://github.com/rafaelvcaetano/melonDS-android/issues/1679)/
  [#1285](https://github.com/rafaelvcaetano/melonDS-android/issues/1285), RTC
  [#1652](https://github.com/rafaelvcaetano/melonDS-android/issues/1652)/
  [#1604](https://github.com/rafaelvcaetano/melonDS-android/issues/1604), library
  [#1519](https://github.com/rafaelvcaetano/melonDS-android/issues/1519)/
  [#1378](https://github.com/rafaelvcaetano/melonDS-android/issues/1378), DSi
  [#1651](https://github.com/rafaelvcaetano/melonDS-android/issues/1651)/
  [#1099](https://github.com/rafaelvcaetano/melonDS-android/issues/1099) and display
  [#1663](https://github.com/rafaelvcaetano/melonDS-android/issues/1663)/
  [#1641](https://github.com/rafaelvcaetano/melonDS-android/issues/1641)/
  [#1619](https://github.com/rafaelvcaetano/melonDS-android/issues/1619) remain
  **partially addressed** in the recorded coverage. Their outstanding parts are in the
  [coverage table](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-28-refinement/ISSUE-PR-COVERAGE.csv).
- Replaces the earlier proposals
  [#1682](https://github.com/rafaelvcaetano/melonDS-android/pull/1682) and
  [#1681](https://github.com/rafaelvcaetano/melonDS-android/pull/1681), recorded as
  closed without merge. Adapted community PRs were not merged or closed upstream
  by this work; refresh remote states and closing keywords before publication.
- [#1665](https://github.com/rafaelvcaetano/melonDS-android/issues/1665) remains
  open in the recorded coverage: a Surface-generation sequence is reproduced in
  Kotlin logic, but the Android EGL cause is not established and no rendering
  fix is claimed. [Persistence/lifecycle investigation](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-31-persistence-lifecycle/README.md).

### Validation and limits

- **Final 2.1.6 / 47:** optimized ARM64 profiling build and lint pass;
  **267 JVM tests**, no failures/errors/skips; lint **0 errors/474 warnings**.
  All **ten NDK executables** pass on API35 ARM64/4KiB and 16KiB, including
  actual NAND/FatFs save replacement and the JNI lease boundary model.
  APK manifest, certificate, native payload and SHA-256 are independently checked.
  [Exact candidate, commands, RED/GREEN and skips](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-31-nightly-2.1.6/README.md).
- **1 November Thor acceptance:** the exact optimized APK is installed and opens
  its populated library. All 17 private files match immediately after update;
  databases, cheats, original library, UID and URI grants are preserved. After
  launch, preferences change only in `last_version`. All 47 protected external
  files match before any separate ROM replacement. Ten native targets pass on
  physical API33/ARM64/4KiB. NDK JNI adapters do not certify ART string lifetimes.
  [Physical update and exact limits](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-01-thor-update/README.md).
- The same optimized APK subsequently completes real field loading, normal
  in-game SAVE and cold Continue on a separately named copy of the current
  Sacred Gold Plus 1.3.1 IT save. The original save remains byte-identical.
  Exportable settings/controller/layout and all ten personal game configurations
  are preserved. The strict ROM save audit retains native variable/seed differences;
  this is one exercised compatibility case, not all-feature or all-game acceptance.
  Full private/cheat DB comparison was not repeated after that ROM stage because
  installation of an additional backup helper was rejected; no retry occurred.

- **31 October first batch, before the pending follow-up:** 267 JVM tests,
  zero failures/errors/skips, lint 0 errors/474 warnings and a debug APK build.
  Eight NDK executables pass on Android API35 ARM64/4KiB and 16KiB. The NAND
  cases use real FatFs/crypto and authored data; RTC uses production resume
  bodies with real core objects. These are separate from whole-app ART.
  [RED/GREEN, identities and limitations](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-31-rtc-nand/README.md).
- **Earlier 28–29 October campaign:** Android ARM64 had 52 passes plus 3 opt-in skips on API33/4KiB and
  API35/4KiB/16KiB; separate real WorkManager and six native targets also pass.
- That earlier evidence covers real GLES failure/fallback, sanitizer regressions,
  native UI captures and independent review.
- **Installed 2.1.5 on Thor Android 13, 29 October:** 52 isolated ART passes plus 3 opt-in skips, a separate
  WorkManager pass and six native passes. The installed optimized build also
  passed copied raw-save/restart, dual-display and read-only cheat/BIOS UI checks.
- [Thor update, preservation checks and before/after measurements](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-29-thor-update/README.md).
- [Before/after tests, exact build identity and limits](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-29-nightly-comparison/VALIDATION.md).
- The physical samples are descriptive; they do not establish a startup, CPU,
  RAM, battery or GPU improvement. An intermittent black display seen before
  updating still lacks a controlled reproducer. Physical microphone/peripheral
  checks, manual TalkBack and slow-provider Activity rotation remain open.
- **Still outside the 2.1.6 update acceptance:** a fresh whole-app ART regression
  campaign, personal NAND/DSi HOME execution and all-game compatibility. The
  previous 2.1.5 device results are not 2.1.6 runtime evidence.

### Core dependency

- Companion core: insert the new PR link after publication is authorized.
- The companion core now rejects incomplete or failed NAND writes; the JNI
  title importer already cleans up a new title when the core returns failure.
  This does not resolve logical HOME quota or prove real-core/ART integration.
- It also stages existing-save replacements before moving the original, with
  backup/rollback and later-import recovery. One-shot metadata faults during
  both rename steps are now red/green on host ASan/UBSan and Android NDK API35
  ARM64 4 KiB/16 KiB; backup cleanup retry is checked. This is not power-loss
  atomic FAT storage. Actual ART/SAF integration and persistent I/O failures
  remain separate checks. [New correction and limits](../2026-11-03-nand-rename-fault/README.md).
- The paired core bounds DS guest Wi-Fi frame conversion after a reproduced
  native LAN-buffer overflow. The [red/green unit](../2026-11-03-wifi-ds-length/README.md)
  includes API35 ARM64 4 KiB/16 KiB runs and an uninstalled APK identity check;
  it does not establish ART or a guest game's network behavior.
- A later DSi WMI correction validates the guest payload against bytes actually
  buffered and the native LAN capacity after a host UBSan out-of-bounds write.
  [Separate component evidence](../2026-11-03-dsi-wmi-length/README.md) includes
  API35 ARM64 4 KiB/16 KiB execution, but no rebuilt APK or ART/guest test.
- Review core first, then pin the accepted upstream revision and restore the
  upstream submodule URL. Repeat relevant checks if the core changes.
- [Full Nightly comparison, commit/file inventories and integration notes](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-29-nightly-comparison/README.md).

### Additional verified core corrections, 3 November

- Enabled Action Replay cheats now require complete instruction and E-code payload words before interpretation. The real ARM7 VBlank callback is red on host ASan/UBSan before the fix and green across ten cases on host and API35 ARM64 4 KiB/16 KiB AVDs. [Evidence and limits](../2026-11-03-action-replay-length/README.md).
- NAND cartridge B7 reads now stay within their authored save allocation and wrap within the selected 4 KiB page, following Arisotura’s upstream cartridge behavior. A near-end ASan read is red before the correction; six host and API35 ARM64 4 KiB/16 KiB component cases are green after it. [Evidence and limits](../2026-11-03-nand-cart-read/README.md).

### Deep Security Scan corrections, 3 November

Deep Security Scan `4c41e000-6ba0-4453-aaa7-3cdbec542687` produced 35 saved
occurrences. None was dismissed as a false positive: all 35 have a concrete
correction in this app/core pair. This app proposal owns or shares these 18:

- `occ_e9fbed34ba4f36c73dc9449a` — Android RCHV loader discards the real buffer length before native deserialization.
- `occ_9423bc5a581f219396ba5434` — Launching a compressed ROM can exceed the configured cache limit and exhaust storage.
- `occ_7912862fc50149068674e372` — DSiWare metadata is imported from cleartext HTTP without authenticity verification.
- `occ_9fbf5f7454cdc1a7a98c3959` — DSi title listing reads fixed-width banner text as an unbounded string.
- `occ_a58108db986cc45682121731` — A malformed external firmware-launch intent crashes the Android emulator.
- `occ_0ead515f26677112d1f828d6` — Untrusted serialized ROM configurations expose or overwrite files using melonDS authority.
- `occ_d540d679331dce207e3adb60` — Null PATH extra crashes the exported singleTask launch path.
- `occ_bc26e0f9273443d666f51fd1` — A tiny cheat database can request a near-2 GiB allocation.
- `occ_1211210d24906e23b17d51f2` — A malformed settings backup can prevent every later app launch.
- `occ_4e4229a143d29c8d477e6cd6` — Cheat XML import does not bound internal entity expansion or parsed text.
- `occ_dec38863aa79dfd2f944a501` — Privileged release workflows execute dependencies without immutable integrity pins.
- `occ_6cb1c55ef7aff5b4986102aa` — RetroAchievements bearer token is included in Android platform backup.
- `occ_f39613af9fae3c25d83a0055` — Signing in can expose RetroAchievements passwords and tokens through URL logs.
- `occ_4ab7c23e479f23f205e15b1f` — Savestate file length drives an uncapped native allocation before validation.
- `occ_3b5415742d7ddbfad77786f5` — A malicious document provider can turn firmware revalidation drift into a controlled heap overwrite.
- `occ_d861c760cdbcae81657434c1` — A direct ROM URI can force a one-gigabyte native allocation.
- `occ_f0001eeaf73d4f180cc706e6` — A truncated DSiWare file can create an invisible, non-reimportable NAND title.
- `occ_71e386607ebf94f39d022a9f` — A 32-bit URI hash lets one compressed ROM substitute another cached ROM.

The companion core proposal lists the remaining native occurrences and the
three shared boundaries. The complete per-occurrence fixes, RED/GREEN evidence,
raw-source provenance, independent verification procedure and explicit limits
are in the [security remediation report](../2026-11-03-security-remediation/README.md).

Fresh candidate validation: 311 app JVM tests plus 1 rcheevos API test pass
with zero failures/errors/skips; the ARM64 Nightly debug APK and lint complete;
163 native security controls pass
under ASan/UBSan; DNS, JNI NAND and the complete host suite pass. The optional
JIT differential test remains skipped because it requires an external ROM.

The final security code identity is app `a77ccaaa` with core `65952aec`. A
separate adversarial pass after the first 35 fixes also closed the following app
bypasses before this identity was recorded:

- unknown-size ZIP extraction now keeps temporary plus retained cache within
  the live quota; the measured regression falls from 40 bytes to at most 32
  bytes for a 32-byte limit;
- ROM and firmware shortcuts use explicit components, while the exported ROM
  contract accepts only granted `Intent.data` content URIs;
- XML import uses the parser's lexical DTD callback and a per-game aggregate
  cap rather than a byte-marker heuristic;
- settings restore excludes derived timestamps, stale version skips and
  authorization-bearing camera URIs while preserving the real update boolean;
- controller and layout restore validate/canonicalize the actual DTOs, reject
  duplicate layout IDs and pathological counts/ranges, preserve the other
  display target and recover interrupted `AtomicFile` writes before consumers;
- both Android backup rule formats exclude private file-domain state.

The exact RED/GREEN cases, including the cache peak, malformed layout poison,
crash recovery and the one dismissed post-fix provider-exception hypothesis,
are in the [adversarial review](../2026-11-03-security-remediation/ADVERSARIAL-REVIEW.md).

Review note: DSiWare network metadata now fails closed unless a trusted HTTPS
source is explicitly configured. It never falls back to the legacy HTTP endpoint.
GitHub Actions are pinned to independently resolved full commits and the Gradle
9.7.1 distribution is pinned to its official SHA-256; CI enforces both policies.
