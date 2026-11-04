# Core PR draft — not published

**Title: Fix core save handling and native memory bounds**

Source: `Umberto-DEV/melonDS-android-lib:main` →
`rafaelvcaetano/melonDS-android-lib:master`. Updated 4 November 2026.
Publish only after the owner's explicit approval.

Submission scope: this is the short fork review summary. Before opening an upstream PR, narrow its scope to one feature or fix, following the [maintainer request](https://github.com/rafaelvcaetano/melonDS-android/pull/1672#issuecomment-5637055233). No upstream PR is submitted here.

---

This improves DS/DSi compatibility and handles invalid input and failed storage
operations more safely in the core used by Android Nightly. The comparison
starts from the official Android core `431ab4bd`.

- Validate ROM headers, cartridge transfers and firmware ranges before reading
  or writing native buffers. Check restored scheduler, graphics, audio, DSP,
  camera, RTC, AES and DMA state before using it.
- Keep the previous NAND save generation during replacement, restore it when
  an operation fails, and support a later retry. Reject short reads and writes
  instead of adopting incomplete data.
- Bound DS and DSi Wi-Fi frame lengths, peripheral register access and work
  requested by emulated software. Validate complete Action Replay instructions,
  share one work budget per frame and resume a deferred cheat fairly.
- Correct JIT teardown, memory mapping and code invalidation. Fold a Thumb
  literal only when writes to its memory can invalidate the generated code.
- Apply DS/DSi register and Wi-Fi power-saving rules consistently; avoid
  redundant renderer configuration and unused rendering work.

The [core overview](https://github.com/Umberto-DEV/melonDS-android-lib/blob/main/README.md)
links the changes and their evidence. The [individual finding records](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-followup/FINDING-DETAILS.md)
provide previous behavior, fixes, tests and proof limits. Validation includes
host sanitizers, native Android ARM64 tests on 4 KiB and 16 KiB page sizes,
and execution through the paired app. Host checks and component tests do not
certify all games or physical-device behavior. See the [validation report](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-followup/README.md).

The paired app provides a signed UD Nightly [review build](https://github.com/Umberto-DEV/melonDS-android/releases/tag/ud-nightly-2.1.8)
with its exact APK/source identity and acceptance results. The app build checks out
this core recursively at its pinned Git commit and compiles it into the APK;
users do not install a separate library. The [paired legacy-package physical update](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-04-thor-update/README.md) preserves the inventoried data; physical game compatibility remains separate. Review and merge core first, then pin
the app to the accepted upstream revision.

Compatibility changes needing review include blocking private/loopback network
targets and a changed LAN player representation. DSi HOME/quota, persistent
storage faults and game-specific reports retain their [individual scope](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-28-refinement/ISSUE-PR-COVERAGE.csv).
No all-game or performance improvement is claimed.

The teardown cause from [melonDS-emu/melonDS #2537](https://github.com/melonDS-emu/melonDS/issues/2537)
is corrected locally; this does not close that separate upstream issue.
This replaces Android-lib #18/#19, closed without merge. B7 save-page behavior
adapts Arisotura's [merged cartridge refactor #2595](https://github.com/melonDS-emu/melonDS/pull/2595).
The teardown report credits maximxlss and RSDuck. Existing human authorship,
licenses and credits remain preserved.
