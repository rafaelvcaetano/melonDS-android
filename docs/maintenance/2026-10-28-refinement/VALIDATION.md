# Repeatable validation for the Android refinement

Work on `main`. Preserve existing files and run on a dedicated AVD with synthetic
inputs; never target a device by an implicit ADB default. Record APK hashes,
signing certificate, frontend Build ID, source diff and core revision before
claiming equivalence with any historical APK.

## Build and local checks

Use the repository's installed SDK/NDK and JDK21. From the repository root:

```sh
python3 tools/android-tests/renderer-fixture/generate.py
sh ./gradlew :app:testGitHubNightlyDebugUnitTest :app:assembleGitHubNightlyDebug :app:assembleGitHubNightlyDebugAndroidTest :app:lintGitHubNightlyDebug -Pandroid.injected.build.abi=arm64-v8a --max-workers=2 --console=plain
bash tools/host-tests/microphone/lifecycle/run-concurrency.sh
bash tools/host-tests/renderer-fallback/run.sh
bash tools/android-tests/build.sh --abi arm64-v8a
git diff --check
```

The generated original homebrew is only for the opt-in GLES test. No commercial
ROM, BIOS or user save is required by the new regressions. The concurrency and
renderer host harnesses preserve production source provenance and fail on
sanitizer diagnostics. They are not Android runtime or physical-driver evidence.

The final JVM XML contains 267 tests, zero failures/errors/skips. Lint contains
zero errors and 474 warnings. Do not add focused counts to the full suite total.

## Android ART and provider behavior

Install matching app/test APKs on a dedicated ARM64 AVD. Set `REFINEMENT_SERIAL`
explicitly. The tested package names below belong to the GitHubNightlyDebug
variant; confirm the installed runner if using another variant.

```sh
adb -s "$REFINEMENT_SERIAL" shell am instrument -w -r me.magnum.melonds.nightly.dev.test/androidx.test.runner.AndroidJUnitRunner
adb -s "$REFINEMENT_SERIAL" shell am instrument -w -r -e class me.magnum.melonds.common.workers.CheatImportWorkManagerTest -e realWorkManager true me.magnum.melonds.nightly.dev.test/androidx.test.runner.AndroidJUnitRunner
```

Require 52 status-code-zero passes and 3 assumption skips in the default suite,
then one pass in the separate WorkManager invocation. The runner prints 55 cases,
which includes the three opt-in skips. ADB exit zero alone does not establish a
successful test run. The opt-in UI and real-GLES paths are exercised separately.
Apply an outer bounded host watchdog; on timeout preserve evidence and stop only
the selected test/target processes before another run.

The final candidate passes on Android 13/API33 ARM64/4KiB and Android 15/API35
ARM64/4KiB/16KiB. The Android tests cover actual Room/JNI, remote provider opens,
pipes/offset descriptors and four BIOS copy/publication/cancellation cases.
The WorkManager case uses real scheduling, unique KEEP work and foreground
notification lifecycle, with only an isolated database dependency.

The cancellation oracle waits for actual blocked I/O, cancels, and requires
termination before the fixture provides EOF. Database contents must be identical
after failure/cancellation, then a fresh import must succeed. BIOS oracles require
exact bytes, source preservation, both stream closes before rename, an explicit
error on rename refusal, a named noncanonical partial when deletion is refused,
and a subsequent full retry. No provider test promises interruption of arbitrary
unchanged metadata queries or permanently blocked external I/O.

## Native Android and real GLES

After the NDK build, execute each target with an explicit serial:

```sh
bash tools/android-tests/run.sh --serial "$REFINEMENT_SERIAL" --test JitInvalidationTest
bash tools/android-tests/run.sh --serial "$REFINEMENT_SERIAL" --test MicInputGainTest
bash tools/android-tests/run.sh --serial "$REFINEMENT_SERIAL" --test MicSourceSwitchTest
bash tools/android-tests/run.sh --serial "$REFINEMENT_SERIAL" --test NandJniTest
bash tools/android-tests/run.sh --serial "$REFINEMENT_SERIAL" --test StorageNandTest
bash tools/android-tests/run.sh --serial "$REFINEMENT_SERIAL" --test RendererFallbackTest
```

All six targets pass on the three environments above. RendererFallbackTest
executes ten scenarios with exact extracted production function bodies and typed
factory adapters; the original function had seven sanitizer failures and three
success controls. This is distinct from the [whole-app GLES test](../../../tools/android-tests/renderer-fixture/README.md),
which invokes the actual compiler/factory through normal Activity/JNI launch.
On a blank GLES3.1 API33 AVD, Software control, Compute failure and OpenGL failure
all pass with two advancing homebrew phases, actual presentation, unchanged
preferences and normal teardown. Follow that README's capability and watchdog
requirements; GLES3.2 or a skipped opt-in does not prove the failure path.

## Visual acceptance and remaining measurements

The native capture diagnostic uses an imported synthetic cheat database and
`-e captureUi true -e captureLabel NAME`, optionally `captureIme true` and
`captureImeDone true`. Inspect pixels and window bounds; a successful screenshot
operation is not a passing layout assertion. The final matrix covers 320dp
portrait, 640dp landscape, IT/EN, light/dark, real IME and large text; earlier
captures also cover 130% text. System-bar contrast was corrected after a real
white-on-white capture. Done returns the explicit actions without confirmation.

Native Activity rotation during external-provider BIOS copy, spoken TalkBack,
physical mic/controller/display behavior, ordinary startup duration and
CPU/GPU/energy comparisons remain separate gates. The selected GPU experiment
has not been executed; no GPU visibility or performance acceptance is claimed.
Do not use emulator timings, logical texture sizes or SurfaceFlinger refreshes
as physical Thor improvements.

Retain one canonical current build and previous verified fallback plus small
logs/XML, source hashes and reproducer scripts. Stop producers before removing
regenerable intermediates. Preserve original ROM/BIOS/save fixtures and the prior
campaign's unique evidence even when stored under a directory named `build`.
