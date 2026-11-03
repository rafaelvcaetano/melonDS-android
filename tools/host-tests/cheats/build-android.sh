#!/usr/bin/env bash
set -euo pipefail
repo="$(cd "$(dirname "$0")/../../.." && pwd)"
ndk="${ANDROID_NDK_HOME:-$HOME/Library/Android/sdk/ndk/28.0.13004108}"
out="$repo/build/host-tests/cheats/ActionReplayLengthTest-android35-arm64-appcore"
mkdir -p "$(dirname "$out")"
"$ndk/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android35-clang++" \
    -std=c++17 -O1 -g0 -Wl,-s -fdata-sections -ffunction-sections -Wl,--gc-sections \
    -fsanitize=undefined -fno-sanitize=alignment -fno-sanitize-recover=all \
    -static-libsan -static-libstdc++ -DANDROID -DARCHITECTURE_ARM64=1 \
    -DJIT_ENABLED -DXXH_NO_INLINE_HINTS=1 -DOGLRENDERER_ENABLED \
    -DEGL_EGLEXT_PROTOTYPES '-DMELONDS_GL_HEADER=<GLES3/gl32.h>' \
    -I"$repo/melonDS-android-lib/src" -I"$repo/app/src/main/cpp" \
    -I"$repo/app/.cxx/Debug/532z4415/arm64-v8a/melonDS-android-lib/src" \
    -I"$repo/melonDS-android-lib/src/teakra/include" \
    "$repo/tools/host-tests/cheats/ActionReplayLengthTest.cpp" \
    "$repo/melonDS-android-lib/src/AREngine.cpp" \
    "$repo/tools/host-tests/jit/HostPlatform.cpp" \
    "$repo/tools/android-tests/AndroidDynamicLibrary.cpp" \
    "$repo/app/src/main/cpp/FileModeString.cpp" \
    "$repo/app/.cxx/Debug/532z4415/arm64-v8a/melonDS-android-lib/src/libcore.a" \
    "$repo/app/.cxx/Debug/532z4415/arm64-v8a/melonDS-android-lib/src/teakra/src/libteakra.a" \
    -ldl -lEGL -lGLESv3 -o "$out"
printf '%s\n' "$out"
