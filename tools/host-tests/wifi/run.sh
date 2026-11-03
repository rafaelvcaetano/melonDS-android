#!/usr/bin/env bash
set -euo pipefail
repo="$(cd "$(dirname "$0")/../../.." && pwd)"
out="$repo/build/host-tests/wifi"
mkdir -p "$out"
"${CXX:-clang++}" -std=c++17 -O1 -gline-tables-only \
    -fsanitize=address,undefined -fno-sanitize=alignment -fno-sanitize-recover=all \
    -I"$repo/melonDS-android-lib/src" \
    "$repo/tools/host-tests/wifi/WifiAPLengthTest.cpp" \
    "$repo/melonDS-android-lib/src/WifiAP.cpp" \
    -o "$out/WifiAPLengthTest"
if [[ "$#" -eq 0 ]]; then
    set -- valid zero-payload max-payload tiny short missing-fcs oversized wrong-channel unassociated bad-llc
fi
for mode in "$@"; do
    ASAN_OPTIONS=detect_leaks=0 UBSAN_OPTIONS=halt_on_error=1 "$out/WifiAPLengthTest" "$mode"
done
