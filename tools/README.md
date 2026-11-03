# tools/

## Host tests

The [NAND/FatFs](host-tests/nand-fat/README.md) and
[RTC resume](host-tests/rtc-resume/README.md) integration controls link the real
core and also run as Android native executables. Their scope is narrower than
whole-app ART or device validation.

`tools/host-tests.sh` builds and runs every host-side test. Most need nothing but a C++
compiler; the two under `tools/host-tests/jit/` link the emulator core, so the script builds
it first via `tools/core-host-sanitize.sh` (and skips them, rather than failing, if it can't).

`JitInvalidationTest` direct-boots a synthetic cartridge it assembles itself and proves that
rewriting a literal drops the block that folded it, that invalidation is precise to 16 bytes
instead of clearing the whole 512-byte range, and that a literal outside any code region (in
the DTCM) is neither tracked nor folded. It always runs. `JitDifferentialTest` runs a real ROM
for 600 frames with the JIT and again with the interpreter and compares framebuffer hashes
every 60 frames; it needs `MELONDS_TEST_ROM=/path/to/rom.nds` and skips without it.

    MELONDS_TEST_ROM=~/roms/game.nds tools/host-tests.sh

On Apple Silicon these cover the ARM64 backend. CMake disables the JIT for x86_64 on
macOS; the GitHub Actions native matrix additionally exercises Linux x86_64 and ARM64.
CI sets `REQUIRE_CORE_TESTS=1`, so an unavailable core or JIT backend fails verification.
The ROM-dependent differential test still requires a private ROM fixture and is separate
from the always-run synthetic invalidation test. UBSan findings fail the host checks by
default. Leak detection remains disabled in the core harness; DNS packet tests are a
separate functional check and are not sanitizer-instrumented.

## HWASan opt-in (debug builds)

Build with a native sanitizer enabled (e.g. HWASan on device):

```
sh ./gradlew :app:assembleGitHubNightlyDebug -PnativeSanitize=hwaddress -Pandroid.injected.build.abi=arm64-v8a
```

Requirements: an AVD or device with an arm64 ABI running API 34+, and NDK 27+ (HWASan needs
a recent enough runtime and tag-checking support). It only applies to the `debug` build type.

Reading crashes: `adb logcat -b crash` for the HWASan report, then resolve native symbols
with `ndk-stack`, pointing it at the unstripped objects under
`app/build/intermediates/cxx/Debug/*/obj/arm64-v8a`.

On Android 14+ an HWASan-built APK can run on a regular (non-HWASan) device or emulator, but
only if it ships a `wrap.sh` that sets `LD_HWASAN=1` (see
https://developer.android.com/ndk/guides/hwasan and .../wrap-script); without it dlopen fails
with `TLS symbol "(null)" in dlopened libclang_rt.hwasan-aarch64-android.so`. When
`nativeSanitize` is set, the `debug` build type packages `tools/hwasan-resources/lib/arm64-v8a/wrap.sh`
via a `resources` source set (Android Studio only packages `.so` files straight from `lib/`)
and turns on `useLegacyPackaging`, as the guide requires. Before Android 14 this doesn't work
at all: you need an HWASan build of Android (e.g. a prebuilt Pixel image).
