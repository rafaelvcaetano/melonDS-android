# Published PR — melonDS-android-lib

> Historical text. PR #18 was closed without merge. Use the
> [current unpublished core draft](../maintenance/2026-10-28-main/NEXT-CORE-PR.md)
> and its linked verification report for the next proposal. The test counts and
> device statements below apply only to the dated historical builds.

Updated 24 October 2026. **Published with user approval: https://github.com/rafaelvcaetano/melonDS-android-lib/pull/18**

## Proposed title

Fix DS/DSi compatibility, Wi-Fi power saving and JIT invalidation

## Proposed body

This brings a small set of emulation and stability improvements used by the companion Android build.

- **DS/DSi compatibility:** apply the correct register setup and access rules when running DS games in DSi mode.
- **Wi-Fi:** only power down the transceiver automatically when the emulated power-saving mode requests it.
- **JIT correctness:** avoid folding literals from memory that is not tracked for invalidation.
- **Rendering:** skip unchanged compute-renderer reconfiguration and remove unused edge-data work.
- **Optional networking API:** allow a frontend to supply hostname-to-IPv4 answers. Disabled by default; this does not change the system DNS resolver.

**Companion Android PR:** https://github.com/rafaelvcaetano/melonDS-android/pull/1681. Review as a coordinated pair; merge this core change before updating the app's submodule to the accepted revision. The core remains usable independently. The Android WFC editor uses firmware DNS settings and does not call the optional hostname API.

**Validation:** sanitizer host tests, synthetic JIT invalidation and DNS packet tests. I have played and tested the original paired ventuno 2.1.4 build on an AYN Thor for five weeks (19 September–24 October 2026), with no bugs or regressions observed; Android confirms the installed app's last update was on 19 September. One ROM framebuffer comparison differs between JIT and interpreter identically in stock and custom cores; no x86_64 validation or hardware validation of the additional 24 October app fixes is claimed.

## Notes for approval (historical preparation notes, not part of the published body)

- Base: upstream `431ab4bd0003c4356e25fce89640ae1004579b9b`.
- Reviewed custom core: `bc060f4cb2faa1fc6ef6f4046c509feb5e686342` (seven existing commits).
- The core's new hostname API returns endpoint addresses; it does not redirect questions to a selected DNS resolver.
- No core PR was blindly imported: #17 stays paired with the deferred app audio rewrite; #1/#3 target obsolete bridge code.
- Do not add closing keywords for core issues #9, #8, #4 or #2: none is currently demonstrated fully resolved. Exact issue/PR attribution is in `audit-2026-10-24/ISSUES-TO-REFERENCE.md`.
- Companion links are added only after the user approves publishing.
