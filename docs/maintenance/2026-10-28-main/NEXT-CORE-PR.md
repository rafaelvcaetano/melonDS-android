# Core PR draft — not published

**Title: Harden core state and I/O**

Updated 3 November 2026. Source: `Umberto-DEV/melonDS-android-lib:main`;
target: `rafaelvcaetano/melonDS-android-lib:master`. Baseline `431ab4bd`;
starting core `65952aec`; final core commit `7837518a` on `main`.
Publication remains blocked pending the coordinated
review and explicit approval. Evidence lives in the app repository, so all
links below are absolute. Check final CI/URLs and add the real companion PR
before publication.

---

This fixes DS/DSi compatibility, JIT memory handling and failed NAND writes in
the core used by Android Nightly. It also validates guest and saved-state data
before accessing native buffers, and removes unnecessary renderer work.
The scope includes the earlier Thor changes and the November corrections.

## Compatibility and reliability fixes

| Area | Corrected behavior | Evidence and tests |
| --- | --- | --- |
| DS/DSi registers | Apply console-mode initialization and SCFG access rules consistently | [features, original authors and test contracts](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-28-refinement/INTEGRATION-REPORT.md) |
| Wi-Fi power saving | Power down automatically only when the emulated mode requests it | [features, original authors and test contracts](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-28-refinement/INTEGRATION-REPORT.md) |
| JIT literals/invalidation | Fold tracked literals and use the actual write mask; reject the first untracked AArch64 Thumb/DTCM fold | [baseline and test ledger](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-29-nightly-comparison/VALIDATION.md); [new Thumb RED/GREEN](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-02-core-literal-thumb/README.md) |
| JIT teardown/DTCM | Release mappings while ARM9 exists; correct RAM aliases, window offsets, remapping and mirrors | [baseline and test ledger](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-29-nightly-comparison/VALIDATION.md) |
| NAND imports | Fail on short source/write/close operations; serialize save FAT headers without unaligned stores | [real FatFs/crypto RED/GREEN](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-31-rtc-nand/README.md) |
| Existing NAND saves | Stage replacement, preserve/restore the old generation on failure and recover on a later import | [replacement tests](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-31-persistence-lifecycle/README.md); [rename fault and retry RED/GREEN](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-nand-rename-fault/README.md) |
| DS Wi-Fi frames | Bound conversion to the actual LAN buffer | [DS RED/GREEN and Android 4/16 KiB](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-wifi-ds-length/README.md) |
| DSi WMI frames | Validate payload length against mailbox occupancy and LAN capacity | [DSi RED/GREEN and instrumentation limits](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-dsi-wmi-length/README.md) |
| Action Replay | Require complete instruction and E-code payload words before interpretation | [ARM7 VBlank RED/GREEN and controls](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-action-replay-length/README.md) |
| NAND cartridge B7 | Keep save reads inside the allocation and selected 4 KiB page | [ASan/Android controls and attribution](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-nand-cart-read/README.md) |
| Rendering | Skip unchanged Compute reconfiguration, unused OpenGL edge data/shaders and a misleading cache-error log | [features, original authors and test contracts](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-28-refinement/INTEGRATION-REPORT.md) |

The optional hostname-to-IPv4 override API is retained, empty by default.
It is independent of the Android firmware Wi-Fi editor. OpenGL/Compute and
high-resolution 3D already existed in the base; no new 2D upscaler is included.

## Security corrections

The completed scan recorded **35 unique occurrences**: 17 core-only, 15 app-only
and 3 shared. This proposal covers 20 core/shared entries; the app covers 18.
The union is 35. These counts are not 35 demonstrated Android exploits.

| Area | Correction | Evidence and tests |
| --- | --- | --- |
| Cartridge images/transfers | Validate headers, allocation, DLDI footprint, B7 spans and restored transfer direction/position/length | [per-occurrence correction and controls](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-remediation/FINDINGS.csv); [post-fix before/after and verification guide](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-remediation/ADVERSARIAL-REVIEW.md) |
| Savestate records | Require complete sections, forward progress and section-local reads; cap SRAM lengths | [security report and repeatable commands](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-remediation/README.md) |
| Restored native state | Validate scheduler callbacks, FIFO/audio state, GPU indices/counts and cross-field invariants | [per-occurrence correction and controls](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-remediation/FINDINGS.csv) |
| DNS | Parse only received bytes, reject oversized names and release resolver allocations | [security report and repeatable commands](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-remediation/README.md); [post-fix before/after and verification guide](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-remediation/ADVERSARIAL-REVIEW.md) |
| Network access/LAN | Block slirp private/loopback targets; validate headers, sender/length/AID and player wire fields | [per-occurrence correction and controls](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-remediation/FINDINGS.csv); [post-fix before/after and verification guide](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-remediation/ADVERSARIAL-REVIEW.md) |
| Wi-Fi TX/state | Bound descriptor/frame/WEP spans; preserve valid default-reply phases, use alignment-safe reads, validate countdown/masks and initialize replies | [post-fix before/after and verification guide](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-remediation/ADVERSARIAL-REVIEW.md) |
| Firmware | Validate image geometry and checksum/copy spans; consume a bounded immutable input snapshot | [per-occurrence correction and controls](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-remediation/FINDINGS.csv) |
| DSiWare/NAND | Bound save geometry and fixed-width title strings; reject truncated content before mutation | [per-occurrence correction and controls](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-remediation/FINDINGS.csv) |
| GBA flash | Bound bank/sector accesses and erase alignment; handle allocation failure | [per-occurrence correction and controls](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-remediation/FINDINGS.csv) |
| DSP/graphics | Validate pipe/page spans, transfer parity and scaling dimensions before arithmetic/copy | [per-occurrence correction and controls](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-remediation/FINDINGS.csv) |

The [security report and repeatable commands](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-remediation/README.md) links exact saved titles, all occurrence dispositions and
reproduction commands. All 35 occurrences have source corrections; dynamic
RED/GREEN and source-backed cases are identified separately. The [post-fix before/after and verification guide](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-remediation/ADVERSARIAL-REVIEW.md)
records adjacent/bypass corrections without inventing additional scan IDs.

### Second independent campaign

The completed second scan contributed 66 new occurrences across app and core,
normalized to 48 cause groups. The core portion adds exact SD/NAND I/O and
capacity checks, BPTWL/NWiFi/R4 bounds, finite guest-work budgets and fail-closed
savestate invariants for scheduler, GPU, SPU, DSP, camera, RTC, AES and DMA.
It also initializes guest-visible buffers and preserves the last valid boundary.

The exact per-occurrence disposition is in the existing
[canonical findings register](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-remediation/FINDINGS.csv).
The [second-campaign report](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-followup/README.md)
records scan identity, causes, previous/correct behavior, commands, Android
4/16 KiB execution, compatibility limits and the 2.1.8 AVD update. The R4 FAT
division finding is not applicable to Android because this frontend constructs
the core without SD storage; it is not claimed fixed for desktop frontends.
One informational occurrence belongs to the earlier campaign and is not counted
as a new cause.

## Validation and limits

- Current paired delivery: [2.1.8/49 identity, Android-on-Mac checks and conservative AVD update](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-followup/README.md).
- The final core passed the focused security suite, full host sanitizer build,
  Action Replay budget controls, and 11 Android-native targets on API35 ARM64
  with 4 KiB and 16 KiB pages. These remain distinct from ART and Thor evidence.
- Recorded security validation of `65952aec`: 163 focused host ASan/UBSan
  controls, full core sanitizer build, synthetic JIT/NAND/RTC and separate
  DNS/JNI suites. The paired app passed 312 JVM tests, debug APK build and lint.
  Exact commands and skips: [security report and repeatable commands](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-remediation/README.md).
- Earlier native Android 4/16 KiB results belong to the revisions in each linked
  report. The [earlier Thor 2.1.6/47 acceptance](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-01-thor-update/README.md) predates the security core and is not its physical acceptance.

Host macOS diagnostics do not validate Android runtime or a Mac frontend.
NDK component tests do not certify full ART/SAF integration, game networking,
DSi HOME/quota, persistent storage faults or power-loss atomicity. Slirp policy
and the LAN wire format need compatibility review. No measured FPS, energy or
all-game improvement is claimed.

## Attribution and integration

Preserve existing human authorship and licenses. B7 save-page behavior adapts
Arisotura's merged [cartridge refactor #2595](https://github.com/melonDS-emu/melonDS/pull/2595).
The teardown cause was reported by maximxlss and analyzed by RSDuck in
[#2537](https://github.com/melonDS-emu/melonDS/issues/2537); fixing this Android
fork does not close that separate repository's issue. Compared Thumb proposals
and partial DSi/game issues remain in the [canonical issue/PR register](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-28-refinement/ISSUE-PR-COVERAGE.csv).

This replaces library #18/#19, closed without merge. Review/merge core first,
then point the app at the accepted revision and upstream URL. No replacement
companion PR has been published.
The new report and register rows are currently local; verify their absolute URLs
only after a separately authorized commit/push and before publication.
