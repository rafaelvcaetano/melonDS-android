#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/../../.." && pwd)"
build_dir="$(mktemp -d "${TMPDIR:-/tmp}/melonds-renderer-fallback.XXXXXXXX")"
trap 'rm -rf "$build_dir"' EXIT
python3 "$script_dir/extract.py" "$build_dir/ProductionFunctions.cpp" "${1:-$repo_dir/app/src/main/cpp/MelonInstance.cpp}"
"${CXX:-clang++}" -std=c++17 -O1 -g -Wall -Wextra -fno-omit-frame-pointer \
    -fsanitize=address,undefined,vptr -fno-sanitize-recover=all \
    -I"$script_dir" -I"$repo_dir/app/src/main/cpp" \
    "$script_dir/RendererFallbackTest.cpp" "$build_dir/ProductionFunctions.cpp" \
    -o "$build_dir/renderer-fallback-test"
status=0
for scenario in software compute-success gl-success compute-failure gl-failure \
    gl-to-compute-failure compute-to-gl-failure failure-recovery repeated-failure software-after-failure; do
    "$build_dir/renderer-fallback-test" "$scenario" || status=1
done
exit "$status"
