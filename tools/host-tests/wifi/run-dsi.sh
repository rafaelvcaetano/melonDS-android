#!/usr/bin/env bash
set -euo pipefail
repo="$(cd "$(dirname "$0")/../../.." && pwd)"
out="$repo/build/host-tests/wifi"
mkdir -p "$out"
bash "$repo/tools/core-host-sanitize.sh" >/dev/null
case "$(uname -m)" in
    arm64|aarch64) arch=-DARCHITECTURE_ARM64=1 ;;
    x86_64) arch=-DARCHITECTURE_x86_64=1 ;;
    *) echo "Unsupported architecture" >&2; exit 2 ;;
esac
"${CXX:-clang++}" -std=c++17 -O1 -gline-tables-only \
    -fsanitize=address,undefined -fno-sanitize=alignment -fno-sanitize-recover=all \
    -DJIT_ENABLED "$arch" -I"$repo/melonDS-android-lib/src" \
    -I"$repo/app/src/main/cpp" \
    "$repo/tools/host-tests/wifi/DsiWmiLengthTest.cpp" \
    "$repo/tools/host-tests/wifi/DsiNetPlatform.cpp" \
    "$repo/app/src/main/cpp/FileModeString.cpp" \
    "$repo/app/build/core-host/src/libcore.a" \
    "$repo/app/build/core-host/src/teakra/src/libteakra.a" \
    -o "$out/DsiWmiLengthTest"
if [[ "$#" -eq 0 ]]; then
    set -- valid max-buffered disconnected bad-llc sync short-plen missing-payload oversized-short-len oversized
fi
for mode in "$@"; do
    ASAN_OPTIONS=detect_leaks=0 UBSAN_OPTIONS=halt_on_error=1 "$out/DsiWmiLengthTest" "$mode"
done
