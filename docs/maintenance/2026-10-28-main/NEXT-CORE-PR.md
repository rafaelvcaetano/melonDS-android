# Unpublished core PR draft

**Proposed title: Fix Android core memory and NAND writes**

- Source: Umberto-DEV/melonDS-android-lib main; target: upstream master.
- Baseline: 431ab4bd, the core actually shipped in the latest official Android
  Nightly in the 29 October comparison. Final security candidate: `65952aec`.
- [Final 2.1.6 app/core candidate identity and checks](../2026-10-31-nightly-2.1.6/README.md).
- [Local comparison and full inventory](../2026-10-29-nightly-comparison/README.md).
- Review copy only. Local `main` push was authorized on 3 November; upstream PR publication remains blocked pending a later explicit instruction.
  Check document links after the authorized app push and insert current CI and
  companion-PR links before publication.

---

This updates the core used by the current Android Nightly. It fixes compatibility
and memory-handling problems, reports incomplete NAND writes as failures and
removes unnecessary renderer work.

### What changes

- **DS/DSi compatibility:** apply the correct register initialization and access
  rules when running DS software in DSi mode.
- **Wi-Fi:** avoid powering down the radio when automatic power saving is disabled.
  The later [DS frame-length correction](../2026-11-03-wifi-ds-length/README.md)
  also bounds guest data conversion to the 2048-byte LAN buffer. It is red on
  host ASan before the fix and green on host and API35 ARM64 4 KiB/16 KiB native
  tests afterward. Real-game networking remains open.
  A subsequent [DSi WMI payload correction](../2026-11-03-dsi-wmi-length/README.md)
  checks guest length, actual mailbox occupancy and LAN-buffer capacity. It is
  host UBSan red/green and Android NDK target-source green on API35 ARM64 4 KiB
  and 16 KiB. Full-core NDK UBSan stopped earlier at a distinct SPI cast;
  whole-app networking and DSi HOME remain unverified.
- **JIT memory handling:** track optimized literals correctly, fix RAM/DTCM
  mappings and remapping, release mappings before ARM9 is destroyed, and do not
  fold a literal whose address has no invalidation owner. The last case has a
  fresh host/Android NDK red-green regression in the
  [2 November repository campaign](../2026-11-02-repository-campaign/README.md).
- **NAND import integrity:** full storage and I/O failures previously returned
  success with truncated title data. Check write status, byte count and close
  results for executables and title structure; reject failed source reads.
  Serialize save FAT headers as little-endian bytes instead of unaligned stores.
- **Existing save replacement:** stage the new bytes before moving the old save;
  source/space/write failures preserve the existing generation. Retain a backup
  during commit, attempt rollback and recover it on a later import after an
  interrupted replacement. Failed FatFs renames can have already moved or
  installed an entry, so inspect the destination and backup and restore the old
  generation before returning failure. No power-loss atomicity is promised.
- **Rendering:** keep unchanged Compute settings from rebuilding shaders/buffers;
  skip unused OpenGL edge data and shaders. Replace a misleading cache-error log.
- **Optional DNS API:** allow a frontend to supply hostname-to-IPv4 answers.
  The table is empty by default; ordinary resolver behavior stays unchanged.

### Validation and limits

- **Final paired 2.1.6 candidate:** all ten NDK executables pass on Android
  API35 ARM64/4KiB and 16KiB. The real-core/FatFs tests verify write failures,
  exact remount readback, existing-save preservation and retry. JNI boundary
  tests are reported separately from the core and do not certify ART.
  The optimized app APK, its packaged native payload, signature and build flags
  are checked. The exact APK was subsequently installed on the Thor on 1 November;
  its library opens and all ten native targets pass on physical API33/ARM64/4KiB.
  [Physical update and preservation scope](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-01-thor-update/README.md).
- The paired optimized APK also completes Continue/field/SAVE/cold Continue on
  one copied Sacred Gold Plus 1.3.1 IT save. Original personal data stays intact.
  This exercises a DS game path, not DSi NAND imports, HOME quota or the new
  NAND string leases in ART. The strict ROM data audit is not a blanket PASS;
  its native variable/seed differences and limited post-ROM private-data checks
  are retained in the physical report.
  [Fresh commands, source/payload hashes, independent review and skips](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-31-nightly-2.1.6/README.md).

- **31 October first batch:** the original import returns success with only
  11776 of 65536 executable bytes persisted. Sixteen space/input/save scenarios
  and three read/write/close fault cases pass after correction, with remount
  readback, unrelated-data controls and explicit cleanup/retry. Original
  unaligned save-header stores abort Android UBSan; corrected code passes.
  Eight native executables pass on Android API35 ARM64/4KiB and 16KiB.
  [Reproducer, original/patched evidence and exact boundaries](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-31-rtc-nand/README.md).
- The authored NAND fixture is not bootable HOME firmware. Logical quota,
  ART/JNI integration and power loss remain outside acceptance. Existing-save
  replacement is covered by the [follow-up](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-31-persistence-lifecycle/README.md)
  and fresh Android native tests. One-shot metadata-write failures during both
  rename stages are now covered by the [3 November red/green unit](../2026-11-03-nand-rename-fault/README.md)
  on host and API35 ARM64 4 KiB/16 KiB. Backup cleanup may leave the old entry
  after a successful commit; the next import removes it without changing the
  committed bytes. Persistent faults and ART/SAF remain untested. Interruption
  recovery is on a later import, not merely remount/export.
- The following results belong to the earlier 28–29 October app/core pair;
  they are historical evidence, not fresh verification of the pending update.

- Strict UBSan reproduced the teardown defect. Android ARM64 tests cover the
  RAM/DTCM alias, remapping, 4–32KiB windows and 4/16KiB Android pages.
- The paired campaign ran native regressions on Android 13 and 15. Host JIT/DNS
  checks and ARM64/x86_64 sanitized builds are recorded separately.
- The Thor Android 13 run passes all six native targets; the optimized app also
  loads, saves and resumes a copied game with Compute 6x on its Adreno GPU.
  [Physical checks and measurement limits](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-29-thor-update/README.md).
- [Before/after evidence, commands and boundaries](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-29-nightly-comparison/VALIDATION.md).
- No new 2D upscaler, OpenCL backend, audio rate-matching patch or measured FPS,
  power or image-quality gain is claimed. Whole-app CPU/memory observations
  do not isolate a core change; sustained thermal and all-game checks remain open.
- The final local source/payload identities and physical update checks are
  recorded. Whole-app ART integration for the latest NAND changes, DSi HOME
  behavior and every game remain outside acceptance; earlier device results do
  not establish those properties of the new binary.

### Related work and integration

- The teardown cause corresponds to [melonDS-emu/melonDS#2537](https://github.com/melonDS-emu/melonDS/issues/2537),
  reported by maximxlss and analyzed by RSDuck. This Android-fork fix does not
  close that separate upstream issue or claim to fix every game-specific JIT crash.
- NAND write integrity is related to Android
  [#1651](https://github.com/rafaelvcaetano/melonDS-android/issues/1651) and
  [#1099](https://github.com/rafaelvcaetano/melonDS-android/issues/1099), which remain
  partial cases: physical exhaustion is demonstrated, the HOME quota diagnosis
  is not. No automatic closing reference is proposed for those reports.
- Companion Android proposal: insert the new PR link after authorization.
  Merge core first, then update the app's gitlink/URL to the accepted revision.
- The app's firmware Wi-Fi editor does not depend on the optional DNS API.
- Replaces the earlier proposals
  [#19](https://github.com/rafaelvcaetano/melonDS-android-lib/pull/19) and
  [#18](https://github.com/rafaelvcaetano/melonDS-android-lib/pull/18), recorded as
  closed without merge; preserve contributor attribution and refresh state
  before publication.
- [Historical file inventory, commit history and exact Nightly baseline](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-29-nightly-comparison/README.md).

### Additional verified core corrections, 3 November

- Bound Action Replay instruction headers and E-code payloads before copying imported cheat words to guest RAM. [ARM7 VBlank host sanitizer and Android ARM64 component evidence](../2026-11-03-action-replay-length/README.md). Full APK/ART and real-game cheat behavior remain separate checks.
- Bound NAND cartridge B7 save reads to the allocation and 4 KiB page, adapting Arisotura’s merged [cartridge refactor #2595](https://github.com/melonDS-emu/melonDS/pull/2595) to the Android fork interface. [Host ASan and Android ARM64 component evidence](../2026-11-03-nand-cart-read/README.md). Full APK/ART and real NAND-title behavior remain separate checks.

### Deep Security Scan corrections, 3 November

Deep Security Scan `4c41e000-6ba0-4453-aaa7-3cdbec542687` produced 35 saved
occurrences. None was dismissed as a false positive: all 35 have a concrete
correction in the paired app/core candidate. This core proposal owns or shares
these 20 native boundaries:

- `occ_bf9ea27628798833dda7cf7b` — Malformed cartridge images cause native out-of-bounds reads and secure-area writes.
- `occ_2be586dc4a21fbc0f60e65fd` — Malformed savestate section metadata causes infinite loops and native overreads.
- `occ_4c67e6080ca4afa7cee67a40` — Savestates restore unchecked cartridge transfer indices used in host memory writes.
- `occ_2993485bc81b37540bda66df` — Crafted savestates control native callback, FIFO, audio-buffer, and table indices.
- `occ_6b9635717a6a6448125d3010` — Guest DNS parsing can read beyond the frame and return stale native bytes.
- `occ_46d778442f1d34d3992b75b7` — Running a ROM can proxy traffic to Android localhost and private-network services.
- `occ_8af6b491512413f1e03d2bb8` — Guest DNS queries leak native resolver allocations.
- `occ_9fbf5f7454cdc1a7a98c3959` — DSi title listing reads fixed-width banner text as an unbounded string.
- `occ_2bba18cf5ac157af5fc41bcb` — Guest-controlled Wi-Fi TX descriptors overwrite native state and trigger an oversized copy.
- `occ_6fc7ac8a4bb1383c4b1bb230` — A DSiWare save size can trap NAND import in a nonterminating loop.
- `occ_c7156b64208b9c244aee3dec` — GPU3D savestate restoration trusts array indices and counts before native writes.
- `occ_a558a5b137d2433c0269cf8e` — A crafted custom firmware image can make checksum updates write outside its native buffer.
- `occ_3b5415742d7ddbfad77786f5` — A malicious document provider can turn firmware revalidation drift into a controlled heap overwrite.
- `occ_f0001eeaf73d4f180cc706e6` — A truncated DSiWare file can create an invisible, non-reimportable NAND title.
- `occ_0f5f2f5c076ab2aa0094b993` — GBA flash commands read and overwrite host heap beyond save RAM.
- `occ_199e211e1856d6a8fcbf97fa` — Guest-writable firmware metadata reaches unchecked host buffer copies.
- `occ_9e7a80234cd3366f50ce8d90` — DSi DSP HLE pipe descriptors escape mapped host memory.
- `occ_595441428ec94336e7c4ebf7` — Zero-width DSi graphics command underflows a native transfer length.
- `occ_a134bcef99d03aae124925a6` — Guest-controlled B7 transfers overread ROM memory and zero-length commands underflow into massive copies.
- `occ_c9523ca984164c679e7340bb` — Savestate SRAM lengths drive unbounded cartridge allocations.

The app proposal lists the Android-owned occurrences. The complete mapping,
before/after evidence, raw-source provenance and independent reproduction steps
are in the [security remediation report](../2026-11-03-security-remediation/README.md).

Fresh candidate validation: the ASan/UBSan core build reaches 100%; 163 focused
native security controls pass; the complete host policy/save/JIT/NAND/RTC suite,
DNS and JNI NAND suites pass; and the paired Android candidate passes 312 JVM
tests, ARM64 APK build and lint. The optional differential JIT test is skipped
because repository policy forbids committing the required external ROM.

The final core identity is `65952aec`, consumed by app code commit `a77ccaaa`.
The independent post-fix review also corrected MP default-reply phase handling,
unaligned Wi-Fi words, countdown/client-mask semantics, zero-initialized reply
packets, DLDI footprint handling, GBA allocation failure, the 128 MiB immutable
DSi snapshot cap, the demonstrated DNS QNAME underflow and the complete LAN
wire protocol/player serialization boundary. The same review finished with no
concrete residual in the assigned core and network/DSi paths. See the
[adversarial review and reproduction procedure](../2026-11-03-security-remediation/ADVERSARIAL-REVIEW.md).
