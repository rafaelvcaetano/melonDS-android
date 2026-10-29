# Evidence behind the Nightly update

## What was checked for this comparison

- Read the official release, current asset digest, annotated tag, publishing
  workflow and both upstream branch tips through GitHub's read-only API.
- Download one official APK; verify SHA-256 against the asset metadata and read
  version/package information directly from its manifest.
- Inspect ZIP entries for actual frontend ABI coverage, rather than treating
  transitive dependency libraries as emulator binaries.
- Compare every changed app/core path and enumerate every reachable added commit.
  Record exact repeated patches using stable patch IDs.
- Check upstream source for inherited features and for representative defects:
  backup feature flag, existing DAT parser, pre-import deletion, microphone
  conversion, scan-state cleanup and failed-renderer handling.
- Verify that the recorded candidate files/APKs still match their retained hashes.
- Check report links, CSV/JSON counts, commit conventions and whitespace.

This is a source/provenance audit. It does not rerun the full application suite
or install the official APK. Historical test results below retain their original
source and runtime boundaries.

## Before and after, with the actual baseline identified

| Case | Before | Corrected result | Baseline and evidence |
| --- | --- | --- | --- |
| Amplified microphone samples | Six out-of-range numeric cases fail or overflow | All 11 numeric cases pass with saturation | Faulty conversion also exists in official c42995ca. Retained host/Android callback regressions use the production source. |
| Microphone source/session switch | Negative copy length of −952 bytes or stale cursor | Cursor belongs to the capture buffer | Local pre-fix source and exact controls; this is not a physical microphone sensitivity test. |
| Microphone concurrency | Toggle/configuration/teardown races; atomic flags alone fail controls | Eight TSan scenarios pass with serialized control ownership | Local pre-refinement fork, production translation unit with driver adapters. |
| Failed ROM scan | State remains SCANNING; lists can mutate after emission | Error resets state; scans serialize; snapshots remain stable | The official scan lacks the final cleanup too. Six initial failures, nine final scan/sort cases. No ordinary-startup timing claimed. |
| Cheat import failure | Database is deleted before valid replacement is complete | Exact database preservation on error/cancel, successful subsequent import | Official worker contains the early deletion. Real Room/SQLite regressions and Android providers exercise the corrected path. |
| Blocked import cancellation | A read ignoring interrupt retains parser/work | Owned stream closes; provider-open cancellation and WorkManager retry succeed | Local pre-refinement fork; real pipes/Binder/KEEP/notification tests, not every arbitrary provider. |
| Guided BIOS copy | New fork flow could adopt an incomplete canonical NAND | Unique staging, stream closure before publication, explicit errors/retry | The guided flow is a fork addition, not a screen in the official Nightly. 30 BIOS JVM cases and four Android SAF cases. |
| Large text/keyboard in cheat selector | New fork dialog clips or loses visible actions | Scrollable contents, 48dp targets, single radio action, readable system bars | Fork-added selector. 24 JVM UI cases plus native captures; no spoken TalkBack acceptance. |
| Accelerated renderer creation fails | Software is installed but frontend performs an incompatible cast | Software frames/screenshots continue; requested setting remains | Same unsafe pattern found in official source. Seven baseline sanitizer failures; ten corrected boundary scenarios and real GLES failure runs. |
| DSiWare input/TMD | Short or mismatched data accepted; incomplete read or wrong cleanup | Invalid input rejected; exact 520-byte read or EOF; correct cleanup | Retained JNI/ASan/storage/TMD regressions; quota and real NAND HOME boot are not solved by these tests. |
| JIT teardown | ARM9 accessed after destruction | Mapping cleanup occurs while ARM9 exists | Strict UBSan reproduction; original upstream issue #2537 remains open. |
| DTCM fast memory | RAM write aliases DTCM; remap can assert | Correct windows, offsets, sentinels and remapping | Baseline core reproduction and Android ARM64 tests across 4/16KiB pages; independent fast-memory/literal controls. |

**Do not label every RED log as a run of the downloaded official APK.** Some
regressions used an earlier fork revision, and others repair newly added features.
Source inspection establishes where a defect exists; it is not an APK execution.

## Recorded candidate results

Functional app `89053f0fd96ef2bc616849deb15900e1d6b09f43`, core
`fd7582400bb7e35fe0d36dbb0c89b6766c1a8f2e`:

| Check | Recorded result |
| --- | --- |
| JVM/Robolectric | 267 tests; 0 failures, errors or skips |
| Android lint | 0 errors, 474 warnings |
| Default ART/JNI suite | 52 passes plus 3 opt-in skips on each of API33/4KiB, API35/4KiB and API35/16KiB, all ARM64 |
| Real WorkManager | One separate passing test on each environment |
| Native Android | Six executables pass on all three environments |
| Whole-app GLES | Software control, Compute failure and OpenGL failure pass on API33/GLES3.1 with advancing homebrew frames and presentation |
| Host diagnostics | Mic TSan and renderer ASan/UBSan; supporting evidence, separate from Android execution |
| UI | Native IT/EN, light/dark, large text and actual keyboard/Done; final contrast fix checked |

- BIOS/UI counts above are subsets of the 267 JVM total, not extra tests.
- Android opt-in skips are not passes; separate runs are identified explicitly.
- App APK: `72c3e4363f74da95e9e02df44fa3cba523ddf51ca2216e6992d220682926ec1b`.
- Test APK: `6bd051e69cf85f3c3b8699541ab7a1d4416d2b18abceb4af05b0a60f66f47bcb`.
- Frontend Build ID: `f108ca62588fed32ae60805bed94bad54581d9cd`.
- [Commands and test contracts](../2026-10-28-refinement/VALIDATION.md).
- [Independent review and remaining limits](../2026-10-28-refinement/INDEPENDENT-REVIEW.md).
- [Earlier Android/core evidence](../2026-10-28-main/VALIDATION.md) and
  [bug reproductions](../2026-10-28-android-bugfixes/VALIDATION.md).

## Not established yet

- Physical Thor acceptance of the newest candidate, including microphone,
  controller, displays, sleep/audio and real storage providers.
- Faster normal startup, lower CPU/GPU/RAM use, lower energy use or better 6x image quality.
- Full Activity rotation during a slow external-provider BIOS copy, arbitrary
  uncooperative I/O, external SAF writer races or BIOS/NAND authenticity.
- All-game compatibility, every issue's device-specific symptoms, multiplayer
  servers or outstanding RetroAchievements reports.
- Android ARMv7/x86_64 runtime acceptance of the final candidate. Host x86_64
  checks are not Android x86_64 results.

Performance graphs will use comparable physical-device A/B samples. Test counts,
source line counts and differently built APK sizes are not performance results.
