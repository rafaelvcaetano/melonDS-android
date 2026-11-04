# Maintenance and bug research

Current English release evidence: [UD Nightly2.1.8 packaging, source patches and update checks](2026-11-04-ud-nightly/README.md).

Start here for Android bug work. This index is current as of **3 November 2026**;
dated reports are historical evidence, not fresh acceptance of later builds.

User-facing features and update requirements: [UD Nightly guide](../user-guide/UD-NIGHTLY.md).
The [2.1.8 compatibility reference](2026-11-03-thor-2.1.8/README.md) identifies
the legacy candidate separately from later UD release builds.

Current evidence: [second independent scan, 2.1.8/49 and conservative AVD update](2026-11-03-security-followup/README.md).
The exact optimized APK also passed Continue, SAVE and new-process cold Continue
on a copied SGP 1.2.5 IT ROM/save in the dedicated Android AVD; its original
inputs were rehashed unchanged. Thor remains on the prior installed build.
The [finding-by-finding Markdown view](2026-11-03-security-followup/FINDING-DETAILS.md)
provides source-pinned attack paths, checks, counterevidence, fixes and limits;
the CSV below remains the canonical disposition register.
It records 66 new scan occurrences as 48 normalized cause groups, without
recounting the previous 35, and distinguishes 62 repository-code corrections, two
repository corrections with unobservable external deployment controls, one
Android-non-applicable case and one informational reconciliation. The Security
UI may still show these results as Open; that service state is not an upstream
closure or a contradiction of the documented local disposition.

The previous [2.1.7/48 delivery and Android-on-Mac checks](2026-11-03-nightly-2.1.7/README.md)
is now the retained fallback evidence.
The [app draft](2026-10-28-main/NEXT-APP-PR.md) and
[core draft](2026-10-28-main/NEXT-CORE-PR.md) include the earlier Thor changes
and later corrections. The installed Thor 2.1.6 APK predates the security fixes.
No new physical installation or replacement PR publication follows from this handoff.

Earlier closed unit: [DSi WMI guest payload lengths](2026-11-03-dsi-wmi-length/README.md).
Its LAN-buffer write was red on host UBSan and the correction is green on host
and target-instrumented Android NDK API35 ARM64 4 KiB/16 KiB component runs.
The preceding [DS Wi-Fi frame unit](2026-11-03-wifi-ds-length/README.md) and
[DSi NAND rename recovery](2026-11-03-nand-rename-fault/README.md) remain
separate evidence. No new APK/ART execution is claimed for the DSi WMI fix.
The 2 November [repository-wide inventory and AArch64 Thumb/DTCM literal
correction](2026-11-02-repository-campaign/README.md) remains separate evidence.
Its Deep Security Scan status was reported before the later saved artifacts
became visible. The [3 November scan reconciliation](2026-11-03-scan-reconciliation/README.md)
records the exact canceled scans and retained results. Neither canceled scan
establishes complete security coverage. No ART, physical-device, game or
whole-repository safety claim follows.

Later corrected units: [Action Replay instruction/payload bounds](2026-11-03-action-replay-length/README.md) and [NAND cartridge B7 page reads](2026-11-03-nand-cart-read/README.md). Both have host sanitizer RED/GREEN and Android NDK execution on API35 ARM64 4 KiB/16 KiB. The 2.1.7 APK predates them; 2.1.8 includes them and has separate real-app homebrew and ART/provider checks. Direct DSi WMI/game-specific and Thor acceptance remain pending.

Delivery: [modified Nightly 2.1.6 / 47, APK identity and final checks](2026-10-31-nightly-2.1.6/README.md).
Installed in place on the Thor after explicit authorization:
[2.1.6 physical update, data preservation and native checks](2026-11-01-thor-update/README.md).
The ten native targets pass on API35 ARM64/4KiB and 16KiB and on Thor API33/4KiB;
those executions remain separate from ART and whole-game acceptance.

Latest NAND recheck: [API35 ARM64 4 KiB/16 KiB import and replacement
controls](2026-11-02-nand-recheck/README.md). The four Android-native targets
are component evidence for the earlier core. The subsequent
[rename-fault unit](2026-11-03-nand-rename-fault/README.md) covers one-shot
metadata faults and cleanup retry; ART/SAF, persistent faults, HOME and
logical quota remain outside acceptance.

Earlier focused batch: [RTC resume and NAND import integrity](2026-10-31-rtc-nand/README.md).
Two demonstrated core causes corrected in the working tree; Android native
API35 ARM64/4KiB/16KiB checks are separate from whole-app and HOME quota acceptance.

Follow-up: [save replacement and JNI lease corrections; Surface/teardown limits](2026-10-31-persistence-lifecycle/README.md).

The [1 November physical report](2026-11-01-thor-update/README.md) records the
installed 2.1.6/47 build, data preservation checks and its distinct limitations.

- [Audit method and data/storage rules](AUDIT-METHOD.md)
- [Test tools and their boundaries](../../tools/README.md)
- [Android native test runner](../../tools/android-tests/README.md)

## Repositories and baseline

| Layer | Checkout/repository | Current reviewed identity, 3 November |
| --- | --- | --- |
| Android frontend, Kotlin/JNI, Oboe, SAF | This repository; [upstream](https://github.com/rafaelvcaetano/melonDS-android) | APK 2.1.8/49 embeds source commit `0018cddb`; later documentation commits do not change its payload |
| Android core | `melonDS-android-lib` submodule; [upstream](https://github.com/rafaelvcaetano/melonDS-android-lib) | `main` and app gitlink at `ac5e6ba0`, pushed to the fork |
| Shared core research | [melonDS-emu/melonDS](https://github.com/melonDS-emu/melonDS) | Research reference; a fix there is not automatically in the Android submodule |
| Sacred Gold Plus | Separate sibling checkout/repository | Its owner controls ROM sources, builds and validation; do not modify it for an emulator audit |

The previously installed 2.1.5 APK, exact identity and limitations are in the
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

## Security scan results and repository corrections

The [second independent campaign](2026-11-03-security-followup/README.md) used
completed scan `ab26b107-97dd-4557-9c4b-2942e5df879d`, with complete
coordinator inventory coverage of 3,633 nonignored files including 750 core
files. Its 66 occurrences are appended to the existing canonical
[finding register](2026-11-03-security-remediation/FINDINGS.csv), not placed in
a competing catalogue. The final 2.1.8/49 candidate passed host, JVM, Android
NDK 4/16 KiB, ART/provider and in-place 2.1.7→2.1.8 AVD checks. No Thor install,
publication or upstream status change was performed.

The completed Deep Security Scan `4c41e000-6ba0-4453-aaa7-3cdbec542687`
targets app `6a9d7a1f19cfd44ba7fccdedec9f9196d3e04108` with core `907849b3`.
Its 35 saved occurrences were re-triaged against the actual source paths: all
35 are corrected in the current paired candidate; none was dismissed as a false
positive or accepted without a fix. The [full remediation report](2026-11-03-security-remediation/README.md)
records raw inputs, methods, RED/GREEN measurements, exact saved titles,
per-occurrence disposition, independent verification and limits. Fresh checks
include 163 focused ASan/UBSan controls, the full sanitizer core build and host
suites, 312 JVM tests, ARM64 APK build, lint and immutable CI dependency checks.
The final code identity is app `a77ccaaa` with core `65952aec`; the
[post-remediation adversarial review](2026-11-03-security-remediation/ADVERSARIAL-REVIEW.md)
records the additional bypass checks and their RED/GREEN evidence.

The original Daybreak scan below targets app `1cc24ead` and remains canceled.
Its live context reports `remediationAvailable: false` with the exact reason
`Remediation is available only for successfully completed scans.` It is retained
as historical evidence and is not the completed scan described above.

| Original Security title/rule | Current local correction | Recorded verification |
| --- | --- | --- |
| Guest-controlled Wi-Fi lengths can overflow DS and DSi native LAN buffers (`guest-wifi-length-underflow`, three occurrences) | DS `c8d2f94a`; DSi `23700602` | Separate DS and DSi host sanitizer RED/GREEN and API35 ARM64 4/16 KiB component evidence |
| A malicious DS ROM can overflow the host Wi-Fi bridge heap buffer (`memory-safety.heap-buffer-overflow`) | DS `c8d2f94a`; overlapping DS Wi-Fi cause | Same DS unit; do not count as another independently fixed cause |
| Action Replay E-code length reads beyond the imported code vector (`native-memory-safety.action-replay-payload-bounds`) | `4e989e46` | Host sanitizer RED/GREEN; ten component modes on host and API35 ARM64 4/16 KiB |
| NAND cartridge transfers disclose host memory past the save allocation (`nds-nand-cart-oob-read`) | `907849b3` | Host ASan source over-read RED/GREEN; six component modes on host and API35 ARM64 4/16 KiB; no disclosure exploit collected |

These historical mappings cover four corrected causes across six occurrences in
the canceled scan. They are not substituted for the 35 per-occurrence verdicts
in the completed scan. AArch64 literal and DSi NAND rename fixes remain separate
functional-audit findings. Broad physical-device/game acceptance is still not a
consequence of either scan.
