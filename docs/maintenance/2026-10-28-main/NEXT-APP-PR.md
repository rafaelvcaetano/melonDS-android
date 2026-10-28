# Unpublished Android PR draft

**Publication blocked by the user pending coordinated debugging review.**

Source: `Umberto-DEV:main` → target: `rafaelvcaetano/melonDS-android:master`.

Title: Add Wi-Fi settings and cheat search, improve stability and fix Android CI

This adds Wi-Fi connection settings, searchable cheat selectors and reliability improvements for saves, audio and controls. It also fixes the GitHub Actions setup failure that prevented the previous submission from reaching compilation.

### User-facing changes

- **Wi-Fi connections:** configure three WFC slots and primary/secondary DNS addresses in Settings, and enable or disable slots from the pause menu. DSi edits persist both basic and extended connection blocks.
- **Setup and preferences:** guided BIOS/firmware setup, settings/layout backup and restore, and optional forward-only clock synchronization.
- **Controls and cheats:** hold-to-fast-forward, combination hotkeys, optional fast-forward muting and grouped cheat selectors. Interrupted touches and lost controller focus release held inputs.
- **Cheat search:** search folders, individual cheats and generated selector options. Matching text is highlighted; selecting a result opens the matching row or selector without enabling it. Results use lazy scrolling, and the query survives navigation and loading/restoration transitions.
- **Reliability:** improve save-write failure handling, audio restoration after resume, ROM icon loading and bounded 7z decoding memory. Also fixes LCD/scanline alignment, XML cheat import and temporary JNI references while listing DSiWare titles.

### Additional verified corrections

- Preserve the lid-close pause deadline across Activity stop, stop rendering while hidden, and consume pending pauses before destruction. This is a narrow fix reviewed alongside #1657; the larger focus/rendering rewrite is not included.
- Own emulator session coroutines from initialization and cancel work from previous sessions.
- Clear old FPS positioning constraints when moving the counter.
- Collect cheat-import progress once per fragment lifecycle and register update broadcasts through AndroidX compatibility APIs.
- Complete the missing translations in the seven supported locales, repair Spanish time formatting and Russian plurals, and expose the Scanlines filter in every locale.
- Include the standard memory header directly in SaveManager, so standalone Linux builds do not depend on incidental standard-library includes.

### Android bug remediation after consolidation

- Saturate amplified microphone PCM and keep each capture ring's read cursor with its own session.
- Reject missing/truncated NAND/BIOS input, short or mismatched title metadata, and correctly clean up failed title imports. Read complete520-byte network TMD responses or report EOF.
- Restore both DS screens after external-display disconnect and preserve compatible custom layouts across reconnect plus rotated insets.
- Update resistive-screen coordinates on stationary pointer additions/removals without duplicate press/release events.
- Parse fragmented DAT input, reject truncated/invalid game offsets, and propagate cancellation through all parser workers. Replace XML/DAT cheat databases in one Room transaction, retaining existing/custom data on parse, write or cancellation failure with bounded buffering.

These are independently reproduced component fixes, **not claims that all related game/device reports are solved**. See [source-linked correction and category ledger](../2026-10-28-android-bugfixes/REPORT.md), [PR decisions](../2026-10-28-android-bugfixes/PR-REVIEW.md) and [verification boundaries](../2026-10-28-android-bugfixes/VALIDATION.md). The gain-slider request#1679, DSi quota/HOME reports#1651/#1099, physical Thor offsets and game-specific cheat crashes remain partial/open. Original contributor credits below are unchanged; these additional fixes were developed locally.

### Refinement after the coordinated audit

- Cancel blocked cheat imports through owned streams and provider cancellation, preserving exact Room contents and allowing the next unique KEEP import. Real WorkManager tests include notification removal and retry.
- Serialize microphone control and teardown without adding a blocking mutex to the realtime callback; retain source selection and sample-gain behavior.
- Recover ROM scanning after exceptions, serialize overlapping refreshes, emit stable snapshots and persist the newly selected sorting order.
- Validate guided DS/DSi setup before replacing preferences. Copy through unique temporary documents, close both streams before rename, preserve existing files, and report unreadable sources or failed cleanup with a useful retry action.
- Keep cheat search and selectors within the existing melonDS theme, with 48dp targets, one radio action, large-text layouts and a usable IME flow. Keyboard Done dismisses editing without enabling a cheat.
- Handle a failed Compute/OpenGL factory as Software throughout the frontend, avoiding an incompatible downcast while preserving the requested renderer preference. Actual GLES shader rejection and continuing software frames are covered on Android13.

[Refinement report and verification boundaries](../2026-10-28-refinement/STATUS.md).
These corrections do not claim faster normal startup, lower energy use, better
6x image quality or physical Thor acceptance. No additional core revision is
introduced by this campaign.

### Actions correction

- Explicitly request `platform-tools` instead of the unavailable SDK package `tools`.
- Update checkout, Java setup, Android setup and artifact upload to supported Node 24 versions across the Android workflows.
- Run unit tests and an ARM64 debug APK build for forks and pull requests with read-only repository permissions and no release-signing secrets.
- Restrict signed Nightly publication to upstream `master`; upload verification APKs from their actual build output path.
- Remove the nonexistent `setup-ndk` output reference and place the manual Play Store actor restriction on the job, where GitHub evaluates it.

### Credits and issue coverage

The search UI is adapted from [#1677](https://github.com/rafaelvcaetano/melonDS-android/pull/1677) by [takanuva15](https://github.com/takanuva15), whose coauthor credit is preserved. This extends that work to grouped/generated options and precise navigation. Also includes adaptations of #1606/#1608 (WailAbou), #1625 (dysprosium), #1648 (N3MI-DG), #1589 (DLCSharp), and selected side fixes from #1666 (corigne).

- Fixes #1678: temporary JNI references accumulating while listing DSiWare titles.
- Implements the settings/layout backup and restore requested in #264.
- Partially addresses audio resume (#1644/#1635), clock drift (#1652/#1604), and 7z scanning crashes (#1519/#1378). Optional fast-forward muting is a workaround for #1602; remaining device-specific symptoms and audible distortion are not claimed fixed.

### Core dependency

The replacement core proposal will originate from `Umberto-DEV/melonDS-android-lib:main`; insert its URL after publication is authorized. The app pins core `fd7582400bb7e35fe0d36dbb0c89b6766c1a8f2e`, which preserves the earlier proposal and additionally fixes JIT teardown and DTCM fast-memory aliasing reproduced on Android ARM64. The teardown corresponds to [melonDS-emu/melonDS#2537](https://github.com/melonDS-emu/melonDS/issues/2537). Review the pair together; merge the core first and update the app gitlink and submodule URL to the accepted upstream revision before upstream integration. WFC firmware editing does not require the optional hostname-override API.

Previous core proposal, now closed during branch consolidation: https://github.com/rafaelvcaetano/melonDS-android-lib/pull/19.

### Validation and limits

- Local verification: **267 JVM tests, 0 failures/errors/skips**, ARM64 debug APK compilation and Android Lint with **0 errors**. Lint still reports **474 warnings**.
- Native host checks passed: RTC/audio/input policies, save-write handling, synthetic JIT invalidation under ASan/UBSan, DNS packet handling, and bounded JNI reference lifetime. Undefined-behavior diagnostics now fail the native check instead of merely printing a warning.
- CI verifies JVM tests, lint and ARM64 APK, plus native runtime tests on Linux x86_64 and ARM64. Core CI builds sanitizer-instrumented libraries on both architectures. **Insert final reviewed commit and successful CI links after the coordinated audit; do not reuse the previous submission's run as evidence for this source.**
- Native JIT/microphone/NAND and renderer-fallback regressions pass on Android13 ARM64/4KiB and Android15 ARM64/4KiB/16KiB with strict undefined-behavior checks. The app has52 passing ART/JNI regressions per environment, plus3 opt-in skips; real WorkManager is exercised separately on all three. Native models and real JNI/Room tests are distinguished in the validation report. Game-specific Android interpreter/JIT comparisons remain pending. Leak detection is disabled in the Linux host harness; DNS checks are functional, not sanitizer-instrumented.
- A new functional APK check on Android15 ARM64/16KiB loaded a local Sacred Gold Plus1.2.5 raw save, saved through SAF, cold-restarted the process and reloaded it; original fixtures stayed unchanged. This is a narrow integration check, not a claim about all game freezes.
- A prior Android build received five weeks of user testing on an AYN Thor. These newer corrections have automated coverage but still require Thor acceptance testing, including screen-off sound, display mapping, audio, controller input and storage. This campaign uses dedicated Android virtual devices; the new APK is not installed on the physical Thor.
- The earlier standalone SDK-setup-failure and Actions-warning corrections are preserved. Original evidence remains archived with PR #1682; it does not substitute for fresh verification.

### Screenshots

Wi-Fi settings captured on an AYN Thor from the earlier tested build:

![Wi-Fi connection settings with primary and secondary DNS](https://raw.githubusercontent.com/Umberto-DEV/melonDS-android/main/docs/images/wfc-connection-settings.png)

![Wi-Fi connection settings overview](https://raw.githubusercontent.com/Umberto-DEV/melonDS-android/main/docs/images/wfc-overview.png)

Supersedes #1682 and the earlier #1681. The source is now the fork's sole `main` branch; original community attribution and issue references are preserved.
