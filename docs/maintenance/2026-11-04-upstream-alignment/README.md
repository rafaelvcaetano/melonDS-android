# Upstream RTC alignment — 4 November 2026

Subsequent, separately authorized physical delivery: [Thor 2.1.9/50](../2026-11-04-thor-rtc-update/README.md). The results below retain the original diagnostic APK identity.

The Android fork integrates upstream `master` at
[`fa214944`](https://github.com/rafaelvcaetano/melonDS-android/commit/fa214944bb6be20ba4460a627162541c81c07c7b),
the merge of [Komalis's PR #1664](https://github.com/rafaelvcaetano/melonDS-android/pull/1664).
This source integration does not replace or promote the published UD 2.1.8
pre-release, its immutable assets or the installed Thor APK. A later distributed
binary needs a new identity and a versionCode greater than 49.

## The four commits

The four commits reported as missing are one merge and the three commits of
that same PR, rather than four independent feature submissions. Dates below
use Europe/Rome. The first two patches were authored on 29 August but committed
on 11 September, explaining the different dates shown by Git views.

| Commit | Commit date | Change |
| --- | --- | --- |
| [`d889f4c9`](https://github.com/rafaelvcaetano/melonDS-android/commit/d889f4c906e469eddf295824fe440114976cdf60) | 11 September 2026 | First version: synchronize on virtual lid opening; add option/configuration bridge |
| [`8dd69f02`](https://github.com/rafaelvcaetano/melonDS-android/commit/8dd69f0253acfe0bf08d5e7359b69a0c4f07d611) | 11 September 2026 | Add Activity-resume synchronization and a manual JNI/manager bridge |
| [`cecde028`](https://github.com/rafaelvcaetano/melonDS-android/commit/cecde028f6b1e6537fe8cf546b961c80ccd4c137) | 12 September 2026 | Final design: synchronize on emulator resume; remove lid/Activity-specific calls |
| [`fa214944`](https://github.com/rafaelvcaetano/melonDS-android/commit/fa214944bb6be20ba4460a627162541c81c07c7b) | 4 November 2026, 19:55 CET | Rafael Caetano merges PR #1664 |

The complete PR discussion and each patch were read. The maintainer requested
emulator resume rather than Activity resume: a pause-menu resume need not call
Activity.onResume. The final commit implements that request. It also removes
separate synchronization on lid opening, since closing the virtual lid does
not itself stop the emulated clock. The earlier versions are not applied in
addition to the final version.

## Existing fork behavior and resolution

The fork already had opt-in synchronization on native emulator resume,
including a mutex before releasing the frame loop. The previous
[RTC evidence](../2026-10-31-rtc-nand/README.md) records its real-core controls.
The resolutions preserve that implementation and its established settings:

| Area | Upstream final implementation | Integrated fork behavior |
| --- | --- | --- |
| Resume trigger | Native emulator resume, including pause menu | Existing onResumed path retained |
| Disabled option | No RTC synchronization | Retained, default false |
| Clock correction | Set RTC directly to system date/time | Retain forward-only correction with a 30-second threshold |
| Setting/configuration | sync_rtc_on_resume / syncRtcOnResume | Existing rtc_sync_to_host / rtcSyncToHost retained; no duplicate switch or key migration |
| Stopped session | Resume call inside !stop | Same stop guard adopted, retaining mutex protection |
| Intermediate lid/Activity bridges | Removed by final commit | Not reintroduced |
| Whitespace-only changes | Final-newline changes | Existing fork files retained |

The normal three-way merge had six textual conflicts. Resolution reviewed all
15 upstream-touched paths and preserved existing fork security, save, JNI,
settings and UI work. Relative to the previous fork commit `b5b804be`, the
only production-code change is moving the native resume call inside `!stop`.
The upstream commits remain in history with Benjamin BONVARLET/Komalis's
original attribution and Rafael Caetano's merge attribution. The Android core
gitlink remains `2bb701ff`; the upstream app change does not update that core.

A backward correction can now occur in upstream when guest time is ahead;
the fork deliberately retains its existing forward-only policy. This limits
clock rewriting but does not guarantee every game's time events or networking.
The PR discussion contains an unisolated report of Pokémon Platinum wireless
errors following a time skip. This integration does not reproduce, dismiss or
claim to fix that game-specific report.

## Verification

A focused regression compiles the exact production JNI resume body. Only the
external resume callback is replaced by an observation boundary; real pthread
mutexes and condition variables are used. It checks all eight combinations of
thread-started, stopped and initially-paused state, including callback execution
under the mutex and mutex release afterward.

Before the stop guard it fails with “stopped sessions must not dispatch resume.”
After the change all eight cases pass under host ASan/UBSan. This is a dynamic
control-flow regression, not a demonstrated game exploit or an additional
security-scan finding. An initial missing test include was a harness compile
error, corrected before recording that RED execution.

Commands and source:

```sh
bash tools/host-tests/rtc-resume/run-dispatch.sh
bash tools/host-tests/rtc-resume/run.sh
sh ./gradlew :app:testGitHubNightlyDebugUnitTest :rcheevos-api:test \
  :app:assembleGitHubNightlyDebug :app:lintGitHubNightlyDebug \
  --no-daemon --max-workers=2 -Pandroid.injected.build.abi=arm64-v8a
```

The host RTC suite executes six controls against real core RTC objects:
disabled synchronization, forward catch-up, repeated resume, guest clock ahead,
small drift and a second pause/resume. These remain separate from Android
runtime evidence. The diagnostic .dev APK has versionCode 49 but is not a new
release 49 or an update for the Thor. The published 2.1.8 evidence retains its
original APK hashes.

| Layer | Observed result |
| --- | --- |
| JVM | 345 app tests and 4 API tests passed; no failures, errors or skips |
| Android build | ARM64 GitHub Nightly debug APK built successfully |
| Lint | No errors; 479 warnings retained |
| Host sanitizers | Eight JNI dispatch cases and six real-core RTC controls passed under ASan/UBSan |
| Android NDK | ARM64 API 24 native executables built successfully |
| Android runtime | Six real-core RTC controls passed on each API 35 ARM64 AVD: 4 KiB and 16 KiB pages |
| Real app, 4 KiB AVD | SAF selection, homebrew launch, two pause/menu-resume cycles and normal exit passed; six post-resume frame samples showed both expected screen colors |
| CI integrity | Immutable Actions, Gradle distribution and dependency checksum check passed |

Native commands:

```sh
BUILD_JOBS=2 bash tools/android-tests/build.sh --abi arm64-v8a
bash tools/android-tests/run.sh --abi arm64-v8a --serial emulator-5556 --test RtcResumeTest
bash tools/android-tests/run.sh --abi arm64-v8a --serial emulator-5554 --test RtcResumeTest
python3 .github/check_ci_integrity.py
git diff --check
git -C melonDS-android-lib diff --check
```

Both native AVD runs emitted `Failed to allocate memory using ftruncate!
(Invalid argument)` during core construction; all six assertions subsequently
passed with exit code 0. This warning is not counted as a passing check and
these RTC executables do not establish JIT or frame-rendering acceptance.
The real app run is separate evidence on the 4 KiB AVD; no real-app 16 KiB
run or physical-device run was performed for this source integration.

The diagnostic APK SHA-256 is
`99d3a0d2d21b184b7dbdae66a15a1ce1b62ee811d7927c3cd18ea19f9f1a7cac`
(41,416,182 bytes, `me.magnum.melonds.nightly.dev`, debug/testOnly,
ARM64). Its source is the merge resolution against app `b5b804be` and core
`2bb701ff`, with the tests and documentation in this merge; it is not a
distribution candidate. The homebrew input was the existing generated
131,072-byte `fallback-homebrew.nds`, SHA-256
`e298b528668165047d8bb4d7dfa0df038ddb9a6d91289b6f7a84b55e8b2bd5f4`,
copied to an explicitly named AVD working directory. No commercial ROM,
personal save, firmware or NAND input was used.

An existing AVD resolution override clipped the system folder picker; resetting
to its physical size allowed selection. An optional first frame sampler lacked
Pillow; a standard macOS image conversion was used instead. Both are harness
limitations, not app regressions. Bounded local logs, test XML, APK identity
and UI captures are retained privately. CI results for the merge are attached
to its GitHub commit; earlier CI links do not validate this source change.

## Output and device cleanup

The two campaign-owned AVDs were stopped and verified absent. The 4 KiB AVD
search folder, display size, rotation and font scale were restored. Only the
named homebrew working files were removed after a size/hash manifest; the
canonical input and its generated empty save were retained privately.
Reproducible build outputs totaling 1,968,588,197 bytes were removed after
manifesting each file and retaining the diagnostic APK, logs, test XML and lint
reports. Available space afterward was 17,285,120,000 bytes (about 16.1 GiB).
Existing protected fixtures and unrelated root build data were preserved.

## Issue and release scope

PR #1664 is merged upstream. Issues #1652, #1604 and #1618 remained open at this check;
a merged feature is not a claim to have closed every RTC symptom. The existing
[coverage register](../2026-10-28-refinement/ISSUE-PR-COVERAGE.csv) records that
distinction. The [app PR draft](../2026-10-28-main/NEXT-APP-PR.md) uses the updated
upstream source baseline; historical release reports and patches retain their
original baseline. No upstream PR/comment or new release was published for
this alignment, and no operation was performed on the Thor.
