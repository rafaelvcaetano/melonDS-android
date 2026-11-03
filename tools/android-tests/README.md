# Native regression tests on Android

This builds an Android executable and runs it on an ADB-connected Android device or emulator. The machine running CMake is only a cross-build host; the test never executes there. No APK is installed and no device is created, reset or wiped.

The executable reuses `tools/host-tests/jit/JitInvalidationTest.cpp`, `HostPlatform.cpp`, and `app/src/main/cpp/FileModeString.cpp` unchanged. CMake builds and links the real core and teakra, with JIT enabled and Qt, GDB stub and OpenGL disabled. `AndroidDynamicLibrary.cpp` supplies the same `dlopen`/`dlsym`/`dlclose` operations as the Android frontend, so the core can resolve `ASharedMemory_create` on Android.

## Build without a device

Install Android NDK **28.0.13004108** and SDK CMake **3.22.1**, then run:

```sh
tools/android-tests/build.sh
# Optional Android x86_64 target:
tools/android-tests/build.sh --abi x86_64
```

The ARM64 executable is `app/build/android-native-tests/arm64-v8a/JitInvalidationTest`. Each ABI has its own build directory. The minimum Android API is **24**, matching this app. The build uses static libc++ and static UBSan runtime, with `-fsanitize=undefined -fno-sanitize-recover=undefined`; ASan is not enabled. The ELF still uses Android's system libraries and linker.

The NDK toolchain supplies `ANDROID` consistently to the core and the executable, while Clang supplies `__ANDROID__`; both matter to the current core's platform declarations. `JIT_ENABLED` is inherited from the core target's public interface, and the executable uses the matching architecture definition.

SDK discovery uses `ANDROID_SDK_ROOT`, then `ANDROID_HOME`, then `$HOME/Library/Android/sdk` on macOS or `$HOME/Android/Sdk` on Linux. Overrides: `ANDROID_NDK_HOME`, `CMAKE_BIN`, `NINJA_BIN`, `BUILD_JOBS`. Nothing downloads SDK components automatically.

The configuration follows the [official NDK CMake toolchain workflow](https://developer.android.com/ndk/guides/cmake): the NDK's `android.toolchain.cmake`, explicit ABI/API/STL, and separate output directories for each ABI.

## Run on Android

```sh
tools/android-tests/run.sh --serial DEVICE_SERIAL
# Microphone conversion using the production callback and real Oboe headers:
tools/android-tests/run.sh --serial DEVICE_SERIAL --test MicInputGainTest
# Actual source-switch and capture-ring lifecycle with stream-open adapters:
tools/android-tests/run.sh --serial DEVICE_SERIAL --test MicSourceSwitchTest
# Production JNI functions with explicit JNI/core boundary adapters:
tools/android-tests/run.sh --serial DEVICE_SERIAL --test StorageNandTest
tools/android-tests/run.sh --serial DEVICE_SERIAL --test NandJniTest
# Real FatFs + encrypted authored NAND, and production RTC resume with real core:
tools/android-tests/run.sh --serial DEVICE_SERIAL --test NandFatTest
tools/android-tests/run.sh --serial DEVICE_SERIAL --test RtcResumeTest
# Equivalent device selection:
ANDROID_SERIAL=DEVICE_SERIAL tools/android-tests/run.sh
# Match an Android x86_64 device to its separately built binary:
tools/android-tests/run.sh --abi x86_64 --serial DEVICE_SERIAL
```

Without a selector, ADB's normal single-device selection applies. `ADB_BIN` overrides the SDK's `platform-tools/adb`. The runner prints the device serial, SDK, ABI list and `getconf PAGESIZE`, and rejects an incompatible SDK/ABI before uploading anything.

Only the selected executable is pushed into a fresh `/data/local/tmp/melonds-core-tests.*` directory. The runner sets strict `UBSAN_OPTIONS`, returns the test's actual exit code, and removes its own executable and directory on exit. A disconnected device can prevent cleanup; the runner then reports the exact directory left behind. Output stays on stdout/stderr, so capture it on the build machine if needed:

```sh
tools/android-tests/run.sh --serial DEVICE_SERIAL > android-jit.log 2>&1
```

## What this proves

`NandFatTest` exercises real core/FatFs/crypto with authored data and controlled
file errors: physical exhaustion, explicit cleanup/retry and remount readback.
It does not boot HOME or establish a logical quota. `RtcResumeTest` compiles exact
frontend resume/sync bodies with real NDS/RTC; it does not deliver Activity/JNI
lifecycle events or execute a guest. See their [NAND](../host-tests/nand-fat/README.md)
and [RTC](../host-tests/rtc-resume/README.md) boundary descriptions.

`MicInputGainTest` compiles the unchanged production microphone callback against the real Android Oboe library and supplies eleven constant signed-PCM fixtures. It verifies silence, quiet signals, gain boundaries and positive/negative saturation; the old callback fails six fixtures and triggers float-cast-overflow. It does not open a physical microphone or certify device sensitivity, Bluetooth routing or a game's microphone feature.

`MicSourceSwitchTest` compiles the production audio lifecycle, capture callback and microphone reader, substituting only the frontend/core and stream-opening boundaries. It exercises blow→device switching, a fresh device-microphone session, ring wrap, empty capture, mute and re-enable. A previous shared cursor caused a negative-length copy or stale samples. The copied production translation unit resolves quoted includes to those explicit adapters; its algorithm is not duplicated in tests. This test does not verify actual Oboe route changes or hardware callbacks.

`StorageNandTest` checks production JNI error handling and import identity/cleanup using temporary files and a NAND mount model; `NandJniTest` checks reference lifetimes and title contents using a JNI object model. Neither loads ART or real NAND. The app's `NandOpenFailureTest` under `app/src/androidTest` separately exercises real JNI/platform file failures through Android instrumentation. See `tools/host-tests/nand-jni/README.md` for the exact boundaries.

This exercises native JIT compilation, literal invalidation and fast-memory mapping on Android, including its shared-memory allocation path. The suite compares fast memory on/off, checks a physical DTCM sentinel, moves 4/8/16/32-KiB windows, and checks both mirrors of a 32-KiB window. Android/Linux runs assert that the requested fast-memory mode is active. A reported failure is a failed regression, not a successful harness result. Building alone does not establish any runtime test result.

The shared test platform stubs multiplayer, networking, camera, microphone, persistence callbacks and frontend input. This is not an Android UI, JNI integration, audio, ROM compatibility or lifecycle test. It needs no commercial ROM or BIOS because the test creates a synthetic cartridge. ARM64 and x86_64 are separate targets; a result on one does not verify the other. Device page size matters: 4 KiB and 16 KiB devices exercise different fastmem behavior and should be recorded separately.
