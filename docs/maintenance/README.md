# Maintenance and bug research

Start here for Android bug work. This index is current as of **3 November 2026**;
dated reports are historical evidence, not fresh acceptance of later builds.

Latest verified campaign units: [AArch64 literal handling](2026-11-02-repository-campaign/README.md), [DSi NAND rename recovery](2026-11-03-nand-rename-fault/README.md), [DS Wi-Fi guest frame bounds](2026-11-03-wifi-ds-length/README.md), [DSi WMI payload bounds](2026-11-03-dsi-wmi-length/README.md), [Action Replay payload bounds](2026-11-03-action-replay-length/README.md), and [NAND cartridge read spans](2026-11-03-nand-cart-read/README.md). The [deep-scan reconciliation](2026-11-03-scan-reconciliation/README.md) records canceled partial scans; it does not establish exhaustive coverage.
Local `main` push was authorized on 3 November; upstream PR publication remains blocked.

- [Audit method and data/storage rules](AUDIT-METHOD.md)
- [Prompt for a new investigation chat](NEW-CHAT-PROMPT.md)
- [Test tools and their boundaries](../../tools/README.md)
- [Android native test runner](../../tools/android-tests/README.md)

## Repositories and baseline

| Layer | Checkout/repository | Baseline inspected on 31 October |
| --- | --- | --- |
| Android frontend, Kotlin/JNI, Oboe, SAF | This repository; [upstream](https://github.com/rafaelvcaetano/melonDS-android) | Local `main` at `54bd57ec`; fork five commits ahead of its remote |
| Android core | `melonDS-android-lib` submodule; [upstream](https://github.com/rafaelvcaetano/melonDS-android-lib) | `fd7582400bb7e35fe0d36dbb0c89b6766c1a8f2e`, local `main` |
| Shared core research | [melonDS-emu/melonDS](https://github.com/melonDS-emu/melonDS) | Research reference; a fix there is not automatically in the Android submodule |
| Sacred Gold Plus | Separate sibling checkout/repository | Its owner controls ROM sources, builds and validation; do not modify it for an emulator audit |

The installed 2.1.5 APK, exact identity and limitations are in the
[physical update report](2026-10-29-thor-update/README.md). The
[official Nightly comparison](2026-10-29-nightly-comparison/README.md) distinguishes
upstream from the modified APK; do not compare two differently configured builds
and call the result a regression.

## Reuse these records

| Record | What it establishes | What to refresh |
| --- | --- | --- |
| [Issue categories](2026-10-28-main/ISSUE-CATEGORIES.md) and linked JSON | Historical census: 242 app issues and four Android-library issues, 13 categories | State, body, all comments and relevant PRs for selected cases; not a current count |
| [Per-issue/PR coverage](2026-10-28-refinement/ISSUE-PR-COVERAGE.csv) | Local fixed/partial/unimplemented distinctions | Exact code and test coverage on the chosen app/core pair |
| [Refinement status](2026-10-28-refinement/STATUS.md) and [validation](2026-10-28-refinement/VALIDATION.md) | Existing fixes and JVM/ART/native boundaries | New evidence only after code changes; retain old identities |
| [JIT research](2026-10-28-main/KNOWN-JIT-ISSUES.md) | Provenance, prior patches and Android-specific exclusions | Current source, PR state and target ABI/page size |
| [Update limitations](2026-10-29-thor-update/README.md#remaining-work-and-limits) | Remaining physical/runtime questions | A fresh reproducer; historical non-reproduction is not a fix |

Do not create another full issue dump or a competing status catalogue. Update
selected case records and link dated evidence. Keep implementation plans with
their case; archive a superseded plan by status/link rather than deleting its
only reproducer. Untracked files and pre-existing edits are not disposable.

## Initial research queue

The four issue records below were fetched from the official GitHub REST API on
31 October 2026. They remained open at that check. This is **not** a claim that
they are reproduced in this fork; upstream issue state and local fix state differ.

| Lead | Useful evidence | Next discriminating experiment |
| --- | --- | --- |
| [App #1665: black screen after resume](https://github.com/rafaelvcaetano/melonDS-android/issues/1665) | Reporter: Android 9, output black after lock/unlock or recent apps while audio/input continue; one comment says Software is worse than OpenGL | Activity/Surface generation, both displays, frame/present counters and callback order. Compare Software/OpenGL/Compute on the same synthetic scene. Do not equate it automatically with every local black-screen report. |
| [Shared core #2653: RTC on Android](https://github.com/melonDS-emu/melonDS/issues/2653) | Body concerns changing host time; comment separately concerns elapsed time during sleep | Separate host-clock changes, emulated RTC policy, pause and process restart. Compare guest registers and elapsed host time; exclude ROM anti-clock-change behavior before fixing. |
| [App #1651: DSi logical quota](https://github.com/rafaelvcaetano/melonDS-android/issues/1651) | Reporter suspects physical free space is confused with logical title quota; no comments at check | Trace quota/preflight and short-write/rollback using copied or authored fixtures. Successful JNI import tests do not prove HOME boot or a correct quota. The reporter's diagnosis remains a hypothesis. |
| [App #1678: JNI local references](https://github.com/rafaelvcaetano/melonDS-android/issues/1678) | Precise reported code; already marked locally fixed in the coverage register | Keep production JNI and ART regressions. Confirm the current code instead of rediscovering or reapplying the old fix. |

Read PR discussions and commits as possible solutions, then prove applicability.
For example [PR #1046](https://github.com/rafaelvcaetano/melonDS-android/pull/1046)
concerns R8/ProGuard stripping a JNI model: it is not a DSi logical-quota fix.
Forum, Reddit and video reports can supply triggers; preserve author/date/version
and say whether a video was watched or only its description read.

## Preparation and cleanup performed

No application/core behavior was changed, no emulator/device session was started
and no new app build was made for this preparation. Existing regression sources,
unique evidence, original inputs, user data and canonical APKs were retained.

A manifest-gated cleanup removed **69 generated/duplicate files**, representing
**195,637,248 allocated bytes (186.6 MiB)**: old native `.o/.a/.d` intermediates,
the inactive checkout-local Gradle execution-history cache, and one test-ROM copy
identical by SHA-256 to the retained canonical sibling input. Source, logs and the
runtime test saves were preserved. Build producers were checked inactive and the
ROM owner confirmed no consumers on the Android paths. Selected protected saves,
canonical ROM and APKs were hash-checked before/after. The private manifest and
reproducer are under `local/notes/audit-prep-2026-10-31/`.

Free space after the pass was about **43.5 GiB**; it is an observation, not a
reserved budget. The approximately 2.3 GiB physical-update dossier mostly contains
original backups and unique data: it was not deleted as an old test. Remaining
`build` folders also contain saves, APK baselines and evidence, so their name
alone does not authorize deletion. The old cleanup page now points to the
current rules. Do not delete regression test sources merely because they are old.
