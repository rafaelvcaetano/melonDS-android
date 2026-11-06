# Modified Nightly 2.1.6 — local update candidate

Later acceptance, **1 November 2026**: explicitly authorized
[Thor update, preserved data, library execution and ten native passes](../2026-11-01-thor-update/README.md).
The dated build-time acceptance below remains the record of what had been
executed at delivery on 31 October.

**The APK is built and checked for an update from the documented Thor 2.1.5.
It has not been installed or executed.** Physical acceptance remains pending.
This delivery retains the modified Nightly features, configuration and
contributor credits. The [UD Nightly guide](../../user-guide/UD-NIGHTLY.md)
explains the package and signer requirements for later updates.

## Canonical artifact and identity

The single canonical APK is local, not a published release asset:

`local/notes/nightly-2.1.6-2026-10-31/candidate/melonDS-2.1.6-nightly-profiling-arm64.apk`

| Property | Verified value |
| --- | --- |
| Package | `me.magnum.melonds.nightly.perf` |
| Version | `2.1.6 GH (NIGHTLY) (PROFILING)`, versionCode **47** |
| Mode | Optimized RelWithDebInfo, `-O2`, `-DNDEBUG`; non-debuggable; not testOnly |
| Frontend ABI / minimum Android API | ARM64 / 24 |
| APK bytes | 85446694 |
| APK SHA-256 | `2dc30348bc52d412b6bd8ac6e41774c012ad44e566e01011838b1160b62d05a0` |
| Signing certificate SHA-256 | `41ad82cd4365c73195128975cf0b920699f8833093507957dc2d0f9d6bb0b4b1` |
| Frontend ELF Build ID | `f5111ccfb32a76f5e9bcf114c0b50e175e475b19` |
| Frontend ELF SHA-256 | `d720f0846a3dd4967e02cd6bbf5f76f85ef56c130d4e3c28faaeea8373d9ab7a` |

Package, version, minimum API and flags were read from the packaged manifest
with aapt2; apksigner verification succeeded. ZIP integrity and all native-library
hashes were checked. The packaged frontend matches the optimized CMake output
byte-for-byte and differs from the 2.1.5 frontend. AndroidX helper libraries for
other ABIs are present in the archive; there is only one melonDS frontend, ARM64.
This is not a claim of complete x86_64 or ARM32 application support.

The [Thor baseline](../2026-10-29-thor-update/README.md) is 2.1.5/46, the same
package and certificate. Its retained APK still matches SHA-256
`0ecc829c6510aa4422902a7b415916a03d1052562bc7530d9edbdfefdb2766aa`.
This establishes compatibility with that recorded installation identity, not
a fresh inspection of the disconnected Thor. Recheck the installed identity
immediately before a later authorized update; never uninstall or clear data.

## Source and change records

Both repositories remain on `main`. App base:
`1cf4fa6adf3c7a8621734754d907ea0d27824fe9`; core base:
`fd7582400bb7e35fe0d36dbb0c89b6766c1a8f2e`. The app version/JNI changes, core
fixes, new tests and documentation are **uncommitted local work**. No history
was rewritten or published. Base SHAs alone do not identify the candidate.

`local/notes/nightly-2.1.6-2026-10-31/candidate/identity.json` records the APK,
native payload and functional source hashes. The same dossier retains app/core
patches, test sources and verification commands/results. In particular:

- `MelonDSNandJNI.cpp`: `3bbb3a902bf4ad9fe08ba1e11eac3f3351f57155a3610ff71da113aa492a1c71`.
- `DSi_NAND.cpp`: `f6825bdd87ee02d655821db34c82d9826840c55889cbd6b8e777925f5fefec4c`.

Changes since the documented installed 2.1.5 are the four demonstrated causes
below and the version increment. Earlier setup, cheats, Wi-Fi, controls and
reliability additions remain part of the fork; their inventory and human
credits remain in the [Nightly comparison](../2026-10-29-nightly-comparison/README.md).

| Layer | Previous behavior | Correction and evidence |
| --- | --- | --- |
| Core NAND writes | Import could report success after a short write or I/O error | Check counts/status/close and source reads; [first batch](../2026-10-31-rtc-nand/README.md) |
| Core save header | Unaligned typed stores triggered UBSan | Emit the same little-endian FAT bytes explicitly; [first batch](../2026-10-31-rtc-nand/README.md) |
| Core save replacement | A missing or failing replacement could erase/truncate the previous save | Stage before moving the old save, retain backup, attempt rollback and recover on a later import; [persistence report](../2026-10-31-persistence-lifecycle/README.md) |
| Android JNI | URI string leases were released only when copied; null acquisition continued | Release every successful acquisition and stop on null; [persistence report](../2026-10-31-persistence-lifecycle/README.md) |

The existing case reports remain the evidence ledger. This page records delivery
acceptance and does not create another issue catalogue. No upstream issue or
community PR was closed by this delivery.

## Fresh verification of this candidate

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home sh ./gradlew :app:testGitHubNightlyDebugUnitTest :app:assembleGitHubNightlyProfiling :app:lintGitHubNightlyProfiling -Pandroid.injected.build.abi=arm64-v8a --max-workers=2 --console=plain
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home sh ./gradlew :app:assembleGitHubNightlyProfiling :app:lintGitHubNightlyProfiling -Pandroid.injected.build.abi=arm64-v8a -Pandroid.injected.testOnly=false --max-workers=2 --console=plain
bash tools/host-tests.sh
sh tools/host-tests/nand-jni/run.sh
BUILD_JOBS=4 bash tools/android-tests/build.sh --abi arm64-v8a
```

- **JVM:** 267 tests, zero failures/errors/skips. **Profiling APK build:** succeeds.
  **Profiling lint:** zero errors, 474 warnings. Existing compiler/deprecation
  warnings remain; no claim of a warning-free build.
- The first profiling build had `testOnly=true`. The distribution invocation
  above regenerated the manifest/package with `false`; final packaged flags,
  signature and hashes were verified again. No rejected APK was installed.
- Gradle required access to the installed cache outside the workspace. The first
  restricted invocation failed before building. It was repeated successfully
  with the required access; this was an environment failure.
- **Host ASan/UBSan:** ten suite targets pass; the ROM-dependent
  `JitDifferentialTest` is skipped because no ROM was supplied. The separate JNI
  suite also passes. An initial restricted JIT run failed after `shm_open` was
  denied; the same binary and then the entire suite pass with shared-memory
  access. No Mac-specific product fix was pursued. Core leak detection remains
  disabled as documented by the harness.
- **Independent RED/GREEN:** exporting the original JNI reproduces the lease
  failure. A minimal probe using the original save-replacement function with
  real NAND/FatFs reports `FAT_status=4, prior_bytes_intact=0`; corrected code
  reports `FAT_status=0, prior_bytes_intact=1`. Existing permanent controls cover
  read/write/space failures, retry, remount and equivalent save types.
  The broader original-code replacement run first recorded the two expected
  preservation failures, then stopped when a later readback found the lost
  entry. That exit-2 run is retained separately; the reduced probe supplies the
  clean exit-1/exit-0 comparison without counting the abort as another bug.
- **Android NDK runtime:** all ten executables exit zero on dedicated Android
  15/API35 ARM64 AVDs with **4096-byte and 16384-byte pages**:
  JitInvalidationTest, MicInputGainTest, MicSourceSwitchTest, NandJniTest,
  StorageNandTest, UtfLeaseTest, RendererFallbackTest, RtcResumeTest, NandFatTest
  and ExistingSaveReplacementTest. Only temporary standalone executables were
  copied/run/removed. No APK or package was installed; no personal device used.
  JNI adapter tests remain adapter tests even when executed on Android.
- **Independent source review:** no concrete blocking or minor defect found.
  The reviewer traced manager/JNI/core callers and recorded the limits below.
  App/core `git diff --check` passes.

Raw logs, result JSON, compile commands, manifest/signing output and the bounded
APK verification script are retained in the local dossier. There is no fresh
remote CI result for these uncommitted sources.

## Explicit limits and next tests

The APK itself has not run on ART, an emulator or the Thor. No installation is
authorized. The ten NDK results do not certify the frontend UI, actual ART string
leases, SAF providers, physical microphone, controllers, displays or GPU drivers.
API33, Android 9 and non-ARM64 runtime checks were not performed for this candidate.

FatFs rename is not power-loss atomic. Error injection inside rename/rollback
and backup cleanup remains pending; recovery currently runs on a later
`ImportTitleData` call, not merely remount/export. Existing-save replacement can
require free space for both generations. The new tests do not prove HOME quota
or HOME boot, and no such fix is claimed.

Surface generation/#1665 remains a reproduced logic sequence without matching
Android EGL evidence; concurrent NAND teardown remains a source suspicion.
Neither received a speculative patch. The next useful tests are the bounded
ART/SAF save fault sequence, metadata-write failures during commit, and Android 9
Surface create/destroy/swap instrumentation. No performance or energy improvement
is inferred from these checks.

## Drafts and publication gate

- [Current app overview](../../../README.md#whats-new).
- [Current core overview](https://github.com/Umberto-DEV/melonDS-android-lib#whats-new).
- [Canonical issue/PR dispositions](../2026-10-28-refinement/ISSUE-PR-COVERAGE.csv).

The submission drafts remain local. They distinguish local fixes, partial issue
coverage, adapted community work
and old proposals closed without merge. They link detailed dated evidence and
preserve contributor attribution. Main is the eventual fork source; upstream
master is the target. Draft approval and explicit publication permission remain
separate. No push, PR, release, upstream message or installation was performed.

## Retention and data preservation

The current candidate and the documented 2.1.5 fallback each have one canonical
APK. Earlier original backups and unique evidence were left intact. No ROM,
save, BIOS, firmware, NAND, database, settings, grants or signing material was
changed, and Sacred Gold Plus sources/outputs were not modified.

After the build/test producers and the two diagnostic AVDs stopped, a per-file
manifest removed **404 generated files**, **576884736 allocated bytes
(550.2 MiB)**: compiler objects from this final run, exact duplicate frontend
packaging outputs already retained inside the APK, and reproducible probe
executables/debug symbols. Probe sources, RED/GREEN logs and regression source
tests remain. No build directory was deleted in bulk.

APK/fallback, ten verified NDK executables, production source and AGENTS hashes
were unchanged after cleanup; the pre-existing AGENTS diff still matches the
preserved starting copy. The Android runner left no owned temporary executable
or named input behind. Free space after cleanup was about **35.1 GiB**, above
the required 10 GiB reserve. The local `deletion-manifest.json` records every
removed path, size, hash, reason and retained counterpart.
