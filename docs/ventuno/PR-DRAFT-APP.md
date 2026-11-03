# Published PR — melonDS-android

> Historical text. PR #1681 was closed without merge. Use the
> [current unpublished app draft](../maintenance/2026-10-28-main/NEXT-APP-PR.md)
> and its linked verification report for the next proposal. The test counts and
> device statements below apply only to the dated historical builds.

Updated 24 October 2026. **Published with user approval: https://github.com/rafaelvcaetano/melonDS-android/pull/1681**
Supersedes the September draft. The old `pr-prep/app` branch is stale and must not be submitted.

## Proposed title

Add Wi-Fi connection settings and improve saves, audio and controls

## Proposed body

This adds easier online setup and several everyday improvements to the current Nightly.

- **Wi-Fi connections:** configure three WFC slots and their DNS addresses in Settings; enable or disable slots from the pause menu. Includes more reliable persistence of DSi connection changes.
- **Setup and preferences:** guided BIOS/firmware setup, settings backup/restore, and optional forward-only clock sync.
- **Controls and cheats:** hold-to-fast-forward, combo hotkeys, optional fast-forward muting, and grouped cheat selectors. Interrupted touches and lost controller focus release held inputs.
- **Reliability:** reduce save-write data-loss risks and report failures; restore audio after resume; improve ROM icon loading and bound 7z decoding memory.
- **Other fixes:** align LCD/scanline filters to DS pixels, correct XML cheat import, and release temporary JNI references when listing DSiWare titles (#1678).

Includes adaptations of #1606/#1608 (WailAbou), #1625 (dysprosium), #1648 (N3MI-DG), and #1589 (DLCSharp), plus selected side fixes from #1666 (corigne).

**Issue coverage:**
- Fixes #1678 (temporary JNI references accumulating while listing DSiWare titles).
- Implements the settings/layout backup and restore requested in #264.
- Partially addresses audio resume (#1644/#1635), clock drift (#1652/#1604), and 7z-related scanning crashes (#1519/#1378). Optional fast-forward muting offers a workaround for #1602. Remaining device-specific symptoms and audible fast-forward distortion are not claimed fixed.

**Companion core PR:** https://github.com/rafaelvcaetano/melonDS-android-lib/pull/18. Please review the two together. The app submodule should point to the accepted core revision to include its emulation fixes; the new WFC screen does not require the optional core hostname-override API.

**Validation:** 165 JVM tests pass, ARM64 APK builds, and native/DNS tests pass. I have played and tested ventuno 2.1.4 on an AYN Thor for five weeks (19 September–24 October 2026), with no bugs or regressions observed. Android confirms the installed version was last updated on 19 September. The additional fixes prepared on 24 October have automated regression tests. Lint remains red, and one framebuffer comparison fails identically on stock and custom cores.

Stability references: `MelonDSNandJNI.cpp:79,88,232`, `MelonInstance.cpp:706`, `WfcSettingsStorage.kt:17`.

Wi-Fi connection settings on AYN Thor, captured from the installed ventuno 2.1.4 build:

![WFC connection name, enabled switch, and primary/secondary DNS on AYN Thor](https://raw.githubusercontent.com/Umberto-DEV/melonDS-android/4e7e4516035b66859ecaebc9922cf42303aa7d5c/docs/images/wfc-slot-dns-ventuno-2.1.4.png)

![WFC settings overview on AYN Thor](https://raw.githubusercontent.com/Umberto-DEV/melonDS-android/4e7e4516035b66859ecaebc9922cf42303aa7d5c/docs/images/wfc-overview-ventuno-2.1.4.png)

## Notes for approval (historical preparation notes, not part of the published body)

- Replace companion placeholder after both PRs are opened, as explicitly requested by the user.
- The screenshot is an unedited ADB capture of the installed `2.1.4 GH (NIGHTLY) (PROFILING)` build, versionCode 45, last updated on 19 September 2026. Replace the local image path with a GitHub attachment URL only after publication approval. An additional overview and capture metadata are in `audit-2026-10-24/screenshots/`. The capture does not validate today's code changes on hardware.
- Current scope includes the generic family selector and existing SGP-specific native toggle. Clearly distinguish them if the maintainer requests a split.
- Keep original author attribution; these are adaptations, not claims to authorship of others' work.
- A coordinated pair is accurate. A circular compile dependency is not: the app's changed C++ sources compile against stock core headers too.
- Source references and precise fresh test totals are in `audit-2026-10-24/RESULTS.md`; synchronize before any publication.
- Issue/PR attribution and closure wording are detailed in `audit-2026-10-24/ISSUES-TO-REFERENCE.md`. Only #1678 currently receives an automatic-closing keyword; integration of another author's PR does not mean all of its related issues are solved.
- Do not claim corruption-proof saves, arbitrary live DNS editing, faster touch measurements, every external launcher fixed, online service availability, or every issue resolved.
