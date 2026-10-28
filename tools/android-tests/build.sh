#!/usr/bin/env bash
# Cross-build only. This script never invokes ADB or executes a host test.
set -euo pipefail

usage() {
    echo "Usage: $0 [--abi arm64-v8a|x86_64]"
    echo "Environment: ANDROID_SDK_ROOT/ANDROID_HOME, ANDROID_NDK_HOME, CMAKE_BIN, NINJA_BIN, BUILD_JOBS"
}

abi=arm64-v8a
while [ "$#" -gt 0 ]; do
    case "$1" in
        --abi) [ "$#" -ge 2 ] || { usage >&2; exit 2; }; abi="$2"; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) usage >&2; exit 2 ;;
    esac
done
case "$abi" in arm64-v8a|x86_64) ;; *) usage >&2; exit 2 ;; esac

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [ -z "$sdk_root" ]; then
    case "$(uname -s)" in
        Darwin) sdk_root="$HOME/Library/Android/sdk" ;;
        *) sdk_root="$HOME/Android/Sdk" ;;
    esac
fi
ndk_root="${ANDROID_NDK_HOME:-$sdk_root/ndk/28.0.13004108}"
cmake_bin="${CMAKE_BIN:-$sdk_root/cmake/3.22.1/bin/cmake}"
ninja_bin="${NINJA_BIN:-$(dirname "$cmake_bin")/ninja}"
build_dir="$repo_root/app/build/android-native-tests/$abi"

if [ ! -f "$ndk_root/build/cmake/android.toolchain.cmake" ]; then
    echo "Missing Android NDK toolchain under $ndk_root" >&2
    exit 2
fi
if [ ! -x "$cmake_bin" ] || [ ! -x "$ninja_bin" ]; then
    echo "Missing CMake/Ninja; install SDK CMake 3.22.1 or set CMAKE_BIN and NINJA_BIN." >&2
    exit 2
fi

"$cmake_bin" -S "$repo_root/tools/android-tests" -B "$build_dir" -G Ninja \
    "-DCMAKE_TOOLCHAIN_FILE=$ndk_root/build/cmake/android.toolchain.cmake" \
    "-DCMAKE_MAKE_PROGRAM=$ninja_bin" \
    "-DANDROID_ABI=$abi" \
    -DANDROID_PLATFORM=android-24 \
    -DANDROID_STL=c++_static \
    -DCMAKE_BUILD_TYPE=Debug
"$cmake_bin" --build "$build_dir" --target JitInvalidationTest MicInputGainTest MicSourceSwitchTest NandJniTest StorageNandTest RendererFallbackTest --parallel "${BUILD_JOBS:-8}"
printf '\nAndroid test executable: %s/JitInvalidationTest\n' "$build_dir"
printf 'Run on Android: %s/tools/android-tests/run.sh --abi %s --serial DEVICE_SERIAL\n' "$repo_root" "$abi"
