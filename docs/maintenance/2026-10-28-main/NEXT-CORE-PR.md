# Unpublished core PR draft

**Proposed title: Nightly core update: compatibility, memory fixes and renderer cleanup**

- Source: Umberto-DEV/melonDS-android-lib main; target: upstream master.
- Baseline: 431ab4bd, the core actually shipped in the latest official Android
  Nightly. Candidate: fd758240; upstream master still matches the baseline.
- [Local comparison and full inventory](../2026-10-29-nightly-comparison/README.md).
- Review copy only. Push and PR publication require the maintainer's approval.
  Check document links after the authorized app push and insert current CI and
  companion-PR links before publication.

---

This updates the core used by the current Android Nightly. It fixes compatibility
and memory-handling problems and removes unnecessary renderer work.

### What changes

- **DS/DSi compatibility:** apply the correct register initialization and access
  rules when running DS software in DSi mode.
- **Wi-Fi:** avoid powering down the radio when automatic power saving is disabled.
- **JIT memory handling:** track optimized literals correctly, fix RAM/DTCM
  mappings and remapping, and release mappings before ARM9 is destroyed.
- **Rendering:** keep unchanged Compute settings from rebuilding shaders/buffers;
  skip unused OpenGL edge data and shaders. Replace a misleading cache-error log.
- **Optional DNS API:** allow a frontend to supply hostname-to-IPv4 answers.
  The table is empty by default; ordinary resolver behavior stays unchanged.

### Validation and limits

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

### Related work and integration

- The teardown cause corresponds to [melonDS-emu/melonDS#2537](https://github.com/melonDS-emu/melonDS/issues/2537),
  reported by maximxlss and analyzed by RSDuck. This Android-fork fix does not
  close that separate upstream issue or claim to fix every game-specific JIT crash.
- Companion Android proposal: insert the new PR link after authorization.
  Merge core first, then update the app's gitlink/URL to the accepted revision.
- The app's firmware Wi-Fi editor does not depend on the optional DNS API.
- Supersedes closed, unmerged #19/#18; preserve contributor attribution.
- [All 17 changed files, commit history and exact Nightly baseline](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-10-29-nightly-comparison/README.md).
