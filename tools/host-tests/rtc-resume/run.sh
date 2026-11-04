#!/usr/bin/env bash
set -euo pipefail
repo="$(cd "$(dirname "$0")/../../.." && pwd)"
bash "$repo/tools/host-tests/rtc-resume/run-dispatch.sh"
build="$repo/build/host-tests/rtc-resume"
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
python3 "$repo/tools/host-tests/rtc-resume/extract.py" "$build/ProductionFunctions.cpp"
"${CXX:-clang++}" -std=c++17 -g -O1 -fsanitize=address,undefined -fno-sanitize-recover=all \
    "$jit" "$arch" \
    -I"$repo/melonDS-android-lib/src" -I"$repo/app/src/main/cpp" -I"$repo/tools/host-tests/rtc-resume" \
    "$repo/tools/host-tests/rtc-resume/RtcResumeTest.cpp" "$build/ProductionFunctions.cpp" \
    "$repo/app/src/main/cpp/RtcSync.cpp" \
    "$repo/tools/host-tests/jit/HostPlatform.cpp" "$repo/app/src/main/cpp/FileModeString.cpp" \
    "$core/src/libcore.a" "$core/src/teakra/src/libteakra.a" -o "$build/RtcResumeTest"
ASAN_OPTIONS=detect_leaks=0 UBSAN_OPTIONS=halt_on_error=1 "$build/RtcResumeTest"
