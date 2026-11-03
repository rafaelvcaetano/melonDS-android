#!/usr/bin/env bash
set -euo pipefail
repo="$(cd "$(dirname "$0")/../../.." && pwd)"
build="$repo/build/host-tests/nand-fat"
mkdir -p "$build"
core="$repo/app/build/core-host"
bash "$repo/tools/core-host-sanitize.sh"
jit=-DJIT_ENABLED
if [[ "$(uname -s)/$(uname -m)" == Darwin/x86_64 ]]; then jit=-UJIT_ENABLED; fi
case "$(uname -m)" in
    arm64|aarch64) arch=-DARCHITECTURE_ARM64=1 ;;
    x86_64) arch=-DARCHITECTURE_x86_64=1 ;;
    *) echo "Unsupported core architecture" >&2; exit 2 ;;
esac
extra_sources=("$repo/app/src/main/cpp/FileModeString.cpp")
if [[ -n "${1:-}" ]]; then extra_sources+=("$1"); fi
"${CXX:-clang++}" -std=c++17 -g -O1 -fsanitize=address,undefined -fno-sanitize-recover=all \
    "$jit" "$arch" \
    -I"$repo/melonDS-android-lib/src" -I"$repo/app/src/main/cpp" \
    "$repo/tools/host-tests/nand-fat/NandFatTest.cpp" \
    "$repo/tools/host-tests/nand-fat/FaultPlatform.cpp" "${extra_sources[@]}" \
    "$core/src/libcore.a" "$core/src/teakra/src/libteakra.a" -o "$build/NandFatTest"
ASAN_OPTIONS=detect_leaks=0 UBSAN_OPTIONS="${UBSAN_OPTIONS:-halt_on_error=1}" "$build/NandFatTest" "${@:2}"
"${CXX:-clang++}" -std=c++17 -g -O1 -fsanitize=address,undefined -fno-sanitize-recover=all \
    "$jit" "$arch" \
    -I"$repo/melonDS-android-lib/src" -I"$repo/app/src/main/cpp" \
    "$repo/tools/host-tests/nand-fat/ExistingSaveReplacementTest.cpp" \
    "$repo/tools/host-tests/nand-fat/FaultPlatform.cpp" "${extra_sources[@]}" \
    "$core/src/libcore.a" "$core/src/teakra/src/libteakra.a" -o "$build/ExistingSaveReplacementTest"
ASAN_OPTIONS=detect_leaks=0 UBSAN_OPTIONS="${UBSAN_OPTIONS:-halt_on_error=1}" "$build/ExistingSaveReplacementTest"
